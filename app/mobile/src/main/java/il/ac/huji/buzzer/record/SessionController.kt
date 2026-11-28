package il.ac.huji.buzzer.record

import android.content.Context
import android.os.Build
import il.ac.huji.buzzer.ble.Link
import il.ac.huji.buzzer.ble.SensorPacket
import il.ac.huji.buzzer.ble.SensorPoint
import il.ac.huji.buzzer.ble.SleeveConnection
import il.ac.huji.buzzer.ble.SleeveListener
import il.ac.huji.buzzer.camera.VideoRecorder
import il.ac.huji.buzzer.camera.VideoResult
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the person fills in before a session. */
data class SessionForm(
    val player: String,
    val shotType: String = "free throw",
    val plannedShots: Int? = null,
    val cameraSide: String = "right",
    val cameraDistanceM: Double? = null,
    val notes: String = "",
    val frameRate: Int = 30,
    val sensorRateHz: Int = 240,
)

/** What one session produced. */
data class SessionSummary(
    val folder: File,
    val files: List<File>,
    val video: VideoResult?,
    val sensorSamples: Int,
    val clockPings: Int,
    val warnings: List<String>,
)

/**
 * Runs one recording session: video and hand sensor together, then writes the files named as in
 * data/README.md into one folder:
 * `<base>_video.mp4`, `<base>_frametimes.csv`, `<base>_hand.csv`, `<base>_meta.json`.
 *
 * If the sleeve is not connected, only the video is recorded, and the summary says so.
 */
class SessionController(
    context: Context,
    private val sleeve: SleeveConnection,
    private val video: VideoRecorder,
) {
    private val app = context.applicationContext
    private var active: Active? = null

    private class Active(
        val name: SessionName,
        val folder: File,
        val form: SessionForm,
        val startedAt: Instant,
        val sensor: SensorRecording?,
    )

    val isRunning: Boolean get() = active != null

    fun start(form: SessionForm, onFinished: (SessionSummary) -> Unit) {
        check(active == null) { "a session is already running" }
        val base = sessionsDir(app)
        val name = SessionName.next(LocalDate.now(), form.player, base.list()?.toList() ?: emptyList())
        val folder = File(base, name.base).also { it.mkdirs() }

        val connected = sleeve.state.value.link == Link.CONNECTED
        val sensor = if (connected) SensorRecording(SensorPoint.HAND) else null
        if (sensor != null) {
            sleeve.listener = object : SleeveListener {
                override fun onPacket(packet: SensorPacket, phoneMs: Double) = sensor.addPacket(packet)
                override fun onPing(phoneSendMs: Double, phoneReceiveMs: Double, boardUsRaw: Long) =
                    sensor.addPing(phoneSendMs, phoneReceiveMs, boardUsRaw)
            }
            sleeve.startStreaming(form.sensorRateHz)
        }
        val run = Active(name, folder, form, Instant.now().truncatedTo(ChronoUnit.SECONDS), sensor)
        active = run

        video.start(File(folder, name.file("video", "mp4")), File(folder, name.file("frametimes", "csv"))) { result ->
            onFinished(finish(run, result))
        }
    }

    /** Stops the video. The files are written when the video file is closed. */
    fun stop() = video.stop()

    private fun finish(run: Active, result: VideoResult): SessionSummary {
        val warnings = mutableListOf<String>()
        result.error?.let { warnings.add(it) }

        val sensor = run.sensor
        // Stop after the video, so the sensor data covers the whole video.
        if (sensor != null) {
            sleeve.stopStreaming()
            sleeve.listener = null
        } else {
            warnings.add("The sleeve was not connected. Only the video was recorded.")
        }

        var mapping: il.ac.huji.buzzer.sync.ClockMapping? = null
        if (sensor != null) {
            if (sensor.sampleCount == 0) {
                warnings.add("No sensor samples arrived. hand.csv was not written.")
            } else {
                val file = File(run.folder, run.name.file("hand", "csv"))
                mapping = file.bufferedWriter().use { sensor.writeCsv(it) }
                if (mapping == null) {
                    file.delete()
                    warnings.add("No time ping reply arrived, so the sensor times cannot be put on the phone clock. hand.csv was not written.")
                }
            }
        }

        val stats = sleeve.state.value
        val meta = SessionMeta(
            session = run.name.base,
            player = run.form.player,
            startedAtUtc = run.startedAt.toString(),
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            shotType = run.form.shotType,
            plannedShots = run.form.plannedShots,
            cameraSide = run.form.cameraSide,
            cameraDistanceM = run.form.cameraDistanceM,
            notes = run.form.notes,
            appVersion = appVersion(),
            frameRate = run.form.frameRate,
            timestampSource = result.timestampSource,
            videoFrames = result.videoFrames,
            matchedFrames = result.matchedFrames,
            sleeveAddress = if (sensor != null) stats.address else null,
            sensorRateHz = if (sensor != null) run.form.sensorRateHz else null,
            sensorSamples = sensor?.sampleCount ?: 0,
            packetsReceived = if (sensor != null) stats.received else 0,
            packetsLost = if (sensor != null) stats.lost else 0,
            clockPings = sensor?.pingCount ?: 0,
            clockOffsetMs = mapping?.offsetMs,
            clockRate = mapping?.rate,
            clockWorstRoundTripMs = mapping?.worstRoundTripMs,
        )
        File(run.folder, run.name.file("meta", "json")).writeText(meta.toJson())

        active = null
        return SessionSummary(
            folder = run.folder,
            files = run.folder.listFiles()?.sortedBy { it.name } ?: emptyList(),
            video = result,
            sensorSamples = sensor?.sampleCount ?: 0,
            clockPings = sensor?.pingCount ?: 0,
            warnings = warnings,
        )
    }

    private fun appVersion(): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "unknown"

    companion object {
        /** Sessions are kept in the app's own folder on the phone, see app/README.md. */
        fun sessionsDir(context: Context): File =
            (context.getExternalFilesDir("sessions") ?: File(context.filesDir, "sessions")).also { it.mkdirs() }
    }
}
