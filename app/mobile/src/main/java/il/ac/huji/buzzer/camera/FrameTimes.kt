package il.ac.huji.buzzer.camera

import java.io.Writer
import java.util.Locale
import kotlin.math.abs

/** One frame as the camera reported it: sensor time stamp, and when the report reached the app. */
data class CapturedFrame(val sensorTsNs: Long, val arrivalRealtimeNs: Long)

/** The time of one frame in the video file. */
data class VideoFrameTime(
    val frame: Int,
    /** Sensor time stamp of this frame, in ns. */
    val sensorTsNs: Long,
    /** True if a camera report matched this frame. False if the time comes from the video file alone. */
    val matched: Boolean,
)

/**
 * Gives every frame of a recorded video its time on the phone clock.
 *
 * Two problems are solved here:
 * 1. The frames the camera reported are not always the frames in the file. The encoder can drop
 *    frames, and the first reports can come before the recording starts. So the video frame times
 *    are read from the file and matched to the camera reports.
 * 2. Camera time stamps are on the phone clock (`SystemClock.elapsedRealtimeNanos()`) only when the
 *    camera says its timestamp source is REALTIME. Otherwise an offset is estimated.
 */
object FrameTimes {

    /**
     * Nanoseconds to add to a sensor time stamp to get phone clock time.
     *
     * REALTIME: 0. UNKNOWN: the smallest gap between a frame's time stamp and the moment its report
     * reached the app. A report can only arrive after the frame, so this is the tightest bound we have.
     * It makes frame times late by the shortest report delay, likely a few ms. This is an estimate.
     */
    fun clockOffsetNs(frames: List<CapturedFrame>, realtimeSource: Boolean): Long =
        if (realtimeSource || frames.isEmpty()) 0 else frames.minOf { it.arrivalRealtimeNs - it.sensorTsNs }

    /**
     * Matches the frames of the video file to the camera reports.
     *
     * [captureTsNs]: sensor time stamps of the reported frames.
     * [videoPtsUs]: presentation times of the frames in the file, in microseconds.
     * The file times start at an unknown point, but the gaps between them follow the sensor time
     * stamps. The start that lines up the most frames wins.
     */
    fun align(captureTsNs: List<Long>, videoPtsUs: List<Long>): List<VideoFrameTime> {
        if (videoPtsUs.isEmpty()) return emptyList()
        val pts = videoPtsUs.sorted()
        val caps = captureTsNs.sorted()
        if (caps.isEmpty()) {
            return pts.mapIndexed { k, p -> VideoFrameTime(k, (p - pts[0]) * 1000, false) }
        }
        val period = medianGap(caps) ?: medianGap(pts.map { it * 1000 }) ?: 33_333_333L
        val tolerance = period / 4
        val probe = minOf(pts.size, 60)

        var bestBase = caps[0] - pts[0] * 1000
        var bestScore = -1
        var bestError = Long.MAX_VALUE
        for (j in caps.indices) {
            val base = caps[j] - pts[0] * 1000
            var score = 0
            var error = 0L
            for (k in 0 until probe) {
                val d = abs(nearest(caps, base + pts[k] * 1000) - (base + pts[k] * 1000))
                if (d <= tolerance) {
                    score++
                    error += d
                }
            }
            if (score > bestScore || (score == bestScore && error < bestError)) {
                bestScore = score
                bestError = error
                bestBase = base
            }
        }

        return pts.mapIndexed { k, p ->
            val target = bestBase + p * 1000
            val near = nearest(caps, target)
            if (abs(near - target) <= period / 2) {
                VideoFrameTime(k, near, true)
            } else {
                VideoFrameTime(k, target, false)
            }
        }
    }

    /** Writes frametimes.csv as described in data/README.md. */
    fun writeCsv(out: Writer, frames: List<VideoFrameTime>, offsetNs: Long) {
        out.write(HEADER)
        out.write("\n")
        for (f in frames) {
            val tMs = (f.sensorTsNs + offsetNs) / 1_000_000.0
            out.write(
                String.format(Locale.ROOT, "%d,%.3f,%d,%d\n", f.frame, tMs, f.sensorTsNs, if (f.matched) 1 else 0),
            )
        }
        out.flush()
    }

    const val HEADER = "frame,t_ms,sensor_ts_ns,matched"

    private fun medianGap(sorted: List<Long>): Long? {
        if (sorted.size < 2) return null
        val gaps = sorted.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        return gaps.getOrNull(gaps.size / 2)
    }

    /** The value in [sorted] closest to [x]. [sorted] must not be empty. */
    private fun nearest(sorted: List<Long>, x: Long): Long {
        var i = sorted.binarySearch(x)
        if (i >= 0) return sorted[i]
        i = -i - 1
        return when {
            i == 0 -> sorted[0]
            i == sorted.size -> sorted.last()
            x - sorted[i - 1] <= sorted[i] - x -> sorted[i - 1]
            else -> sorted[i]
        }
    }
}
