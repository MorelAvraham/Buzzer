package il.ac.huji.buzzer.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.Collections

/** What one recording produced. */
data class VideoResult(
    val video: File,
    val frameTimes: File?,
    val videoFrames: Int,
    val matchedFrames: Int,
    val reportedFrames: Int,
    val timestampSource: String,
    val error: String?,
)

/**
 * Records video with CameraX and gives every frame of the file a time on the phone clock.
 *
 * A Camera2 capture callback, added through Camera2Interop, reports the sensor time stamp of every
 * frame the camera takes. After the recording, the frame times in the MP4 file are matched to those
 * reports (see [FrameTimes]) and written to frametimes.csv. No audio is recorded.
 */
@OptIn(ExperimentalCamera2Interop::class)
class VideoRecorder(context: Context) {
    private val app = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(app)
    private val frames = Collections.synchronizedList(ArrayList<CapturedFrame>())

    @Volatile
    private var collecting = false
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val isRecording: Boolean get() = recording != null

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long,
        ) {
            if (collecting) frames.add(CapturedFrame(timestamp, SystemClock.elapsedRealtimeNanos()))
        }
    }

    /** Connects the back camera to [previewView] and gets ready to record at [fps] frames per second. */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, fps: Int, onError: (String) -> Unit) {
        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            try {
                val provider = future.get()
                val range = Range(fps, fps)

                val previewBuilder = Preview.Builder().setTargetFrameRate(range)
                // One callback per capture session. It sees every frame, for preview and video alike.
                Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(captureCallback)
                val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)),
                    )
                    .build()
                val capture = VideoCapture.Builder(recorder).setTargetFrameRate(range).build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                videoCapture = capture
            } catch (e: Exception) {
                onError("Camera could not start: ${e.message}")
            }
        }, mainExecutor)
    }

    /** Starts recording to [videoFile]. frametimes.csv is written next to it, as [frameTimesFile]. */
    @SuppressLint("MissingPermission") // No audio, so only the camera permission is needed.
    fun start(videoFile: File, frameTimesFile: File, onDone: (VideoResult) -> Unit) {
        val capture = videoCapture ?: return onDone(failed(videoFile, "Camera is not ready"))
        frames.clear()
        collecting = true
        val options = FileOutputOptions.Builder(videoFile).build()
        recording = capture.output.prepareRecording(app, options).start(mainExecutor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                collecting = false
                recording = null
                val result = if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                    failed(videoFile, "Recording error ${event.error}: ${event.cause?.message}")
                } else {
                    finish(videoFile, frameTimesFile)
                }
                onDone(result)
            }
        }
    }

    fun stop() {
        recording?.stop()
    }

    private fun finish(videoFile: File, frameTimesFile: File): VideoResult {
        val source = timestampSource()
        val reported = synchronized(frames) { frames.toList() }
        return try {
            val pts = readVideoPtsUs(videoFile)
            val aligned = FrameTimes.align(reported.map { it.sensorTsNs }, pts)
            val offset = FrameTimes.clockOffsetNs(reported, realtimeSource = source == "REALTIME")
            frameTimesFile.bufferedWriter().use { FrameTimes.writeCsv(it, aligned, offset) }
            VideoResult(videoFile, frameTimesFile, aligned.size, aligned.count { it.matched }, reported.size, source, null)
        } catch (e: Exception) {
            failed(videoFile, "Frame times could not be written: ${e.message}")
        }
    }

    private fun timestampSource(): String {
        val info = camera?.cameraInfo ?: return "unknown"
        val value = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
        return if (value == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) "REALTIME" else "UNKNOWN"
    }

    private fun failed(video: File, message: String) =
        VideoResult(video, null, 0, 0, frames.size, timestampSource(), message)

    companion object {
        /** Presentation times of the video frames in the file, in microseconds, sorted. */
        fun readVideoPtsUs(file: File): List<Long> {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: error("no video track in ${file.name}")
                extractor.selectTrack(track)
                val pts = ArrayList<Long>()
                while (extractor.sampleTime >= 0) {
                    pts.add(extractor.sampleTime)
                    extractor.advance()
                }
                return pts.sorted()
            } finally {
                extractor.release()
            }
        }
    }
}
