package il.ac.huji.buzzer.record

import java.util.Locale

/**
 * The details of one session, written as meta.json (data/README.md).
 * Values that are not known are written as null, not guessed.
 */
data class SessionMeta(
    val session: String,
    val player: String,
    val startedAtUtc: String,
    val phoneModel: String,
    val androidVersion: String,
    val shotType: String,
    val plannedShots: Int?,
    val cameraSide: String,
    val cameraDistanceM: Double?,
    val notes: String,
    val appVersion: String,
    // Video
    val frameRate: Int,
    val timestampSource: String,
    val videoFrames: Int,
    val matchedFrames: Int,
    // Sleeve
    val sleeveAddress: String?,
    val sensorRateHz: Int?,
    val sensorSamples: Int,
    val packetsReceived: Long,
    val packetsLost: Long,
    val clockPings: Int,
    val clockOffsetMs: Double?,
    val clockRate: Double?,
    val clockWorstRoundTripMs: Double?,
    val firmwareVersion: String? = null,
    val sleeveVersion: String? = null,
) {
    fun toJson(): String {
        val fields = listOf(
            "session" to session,
            "player" to player,
            "started_at_utc" to startedAtUtc,
            "phone_model" to phoneModel,
            "android_version" to androidVersion,
            "app_version" to appVersion,
            "shot_type" to shotType,
            "planned_shots" to plannedShots,
            "camera_side" to cameraSide,
            "camera_distance_m" to cameraDistanceM,
            "notes" to notes,
            "frame_rate" to frameRate,
            "timestamp_source" to timestampSource,
            "video_frames" to videoFrames,
            "matched_frames" to matchedFrames,
            "sleeve_address" to sleeveAddress,
            "firmware_version" to firmwareVersion,
            "sleeve_version" to sleeveVersion,
            "sensor_rate_hz" to sensorRateHz,
            "sensor_samples" to sensorSamples,
            "packets_received" to packetsReceived,
            "packets_lost" to packetsLost,
            "clock_pings" to clockPings,
            "clock_offset_ms" to clockOffsetMs,
            "clock_rate" to clockRate,
            "clock_worst_round_trip_ms" to clockWorstRoundTripMs,
        )
        return fields.joinToString(",\n", "{\n", "\n}\n") { (k, v) -> "  \"$k\": ${value(v)}" }
    }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is Double -> if (v.isFinite()) String.format(Locale.ROOT, "%.6f", v) else "null"
        else -> v.toString()
    }

    companion object {
        fun quote(s: String): String = buildString {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
