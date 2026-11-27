package il.ac.huji.buzzer.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build

/** What issue #17 needs to know about one camera. */
data class CameraInfo(
    val id: String,
    val facing: String,
    /** REALTIME: frame time stamps are on SystemClock.elapsedRealtimeNanos(). UNKNOWN: they are not. */
    val timestampSource: String,
    /** Highest frame rate of the normal camera modes, from the auto exposure fps ranges. */
    val maxFps: Int?,
    /** Highest frame rate of the slow-motion (high speed) video mode, if the camera has one. */
    val maxHighSpeedFps: Int?,
    val hardwareLevel: String,
)

data class PhoneCameraReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val cameras: List<CameraInfo>,
) {
    val backCameras: List<CameraInfo> get() = cameras.filter { it.facing == "back" }

    /** Rows to paste into issue #17, one per back camera. */
    fun toMarkdown(): String = buildString {
        append("| Phone | Android | Camera | Timestamp source | Max fps | Max slow-motion fps | Hardware level |\n")
        append("|---|---|---|---|---|---|---|\n")
        val rows = backCameras.ifEmpty { cameras }
        for (c in rows) {
            append("| $manufacturer $model | $androidVersion (API $sdk) | ${c.id} ${c.facing} | ")
            append("${c.timestampSource} | ${c.maxFps ?: "?"} | ${c.maxHighSpeedFps ?: "none"} | ${c.hardwareLevel} |\n")
        }
    }
}

object CameraInfoReader {
    fun read(context: Context): PhoneCameraReport {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameras = manager.cameraIdList.map { id -> describe(id, manager.getCameraCharacteristics(id)) }
        return PhoneCameraReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            cameras = cameras,
        )
    }

    fun describe(id: String, c: CameraCharacteristics): CameraInfo {
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraMetadata.LENS_FACING_BACK -> "back"
            CameraMetadata.LENS_FACING_FRONT -> "front"
            CameraMetadata.LENS_FACING_EXTERNAL -> "external"
            else -> "unknown"
        }
        val source = when (c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
            CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
            else -> "not reported"
        }
        val maxFps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.maxOfOrNull { it.upper }
        val maxHighSpeed = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.highSpeedVideoFpsRanges
            ?.maxOfOrNull { it.upper }
        val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "not reported"
        }
        return CameraInfo(id, facing, source, maxFps, maxHighSpeed, level)
    }
}
