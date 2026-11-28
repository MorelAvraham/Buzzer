package il.ac.huji.buzzer.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import kotlin.random.Random

class FrameTimesTest {
    private val period = 16_666_667L // 60 fps

    /** Camera reports for [n] frames at 60 fps with a little jitter, starting at [startNs]. */
    private fun captures(n: Int, startNs: Long = 5_000_000_000L, seed: Int = 3): List<Long> {
        val rnd = Random(seed)
        return List(n) { startNs + it * period + rnd.nextLong(-200_000, 200_000) }
    }

    @Test
    fun matchesEveryFrameWhenTheVideoStartsLate() {
        val caps = captures(300)
        // The recording started at report 12. The file times start at 0.
        val video = caps.drop(12).map { (it - caps[12]) / 1000 }
        val result = FrameTimes.align(caps, video)
        assertEquals(video.size, result.size)
        assertTrue(result.all { it.matched })
        assertEquals(caps[12], result[0].sensorTsNs)
        assertEquals(caps[299], result.last().sensorTsNs)
    }

    @Test
    fun keepsOneRowPerVideoFrameWhenTheEncoderDropsFrames() {
        val caps = captures(200)
        val kept = caps.drop(5).filterIndexed { i, _ -> i % 10 != 7 } // every tenth frame dropped
        val video = kept.map { (it - kept[0]) / 1000 }
        val result = FrameTimes.align(caps, video)
        assertEquals(kept.size, result.size)
        assertTrue(result.all { it.matched })
        for ((k, f) in result.withIndex()) assertEquals(kept[k], f.sensorTsNs)
    }

    @Test
    fun marksFramesWithNoReport() {
        val caps = captures(100)
        // The callback stopped early: the last 20 video frames have no report.
        val all = captures(120)
        val video = all.map { (it - all[0]) / 1000 }
        val result = FrameTimes.align(caps, video)
        assertEquals(120, result.size)
        assertTrue(result.take(100).all { it.matched })
        assertFalse(result.last().matched)
        // An unmatched frame still gets a time from the video file, close to the true one.
        assertEquals(all.last().toDouble(), result.last().sensorTsNs.toDouble(), 1_000_000.0)
    }

    @Test
    fun noReportsGivesTimesFromTheFileOnly() {
        val result = FrameTimes.align(emptyList(), listOf(0L, 33_333L, 66_667L))
        assertEquals(3, result.size)
        assertFalse(result[0].matched)
        assertEquals(66_667_000L, result[2].sensorTsNs)
    }

    @Test
    fun offsetIsZeroForRealtimeAndTheSmallestDelayOtherwise() {
        val frames = listOf(
            CapturedFrame(1_000, 9_000_000),
            CapturedFrame(2_000, 7_002_000),
            CapturedFrame(3_000, 8_003_000),
        )
        assertEquals(0L, FrameTimes.clockOffsetNs(frames, realtimeSource = true))
        assertEquals(7_000_000L, FrameTimes.clockOffsetNs(frames, realtimeSource = false))
    }

    @Test
    fun writesCsvOnThePhoneClock() {
        val out = StringWriter()
        FrameTimes.writeCsv(
            out,
            listOf(VideoFrameTime(0, 1_000_000_000, true), VideoFrameTime(1, 1_016_666_667, false)),
            offsetNs = 2_000_000,
        )
        val lines = out.toString().trim().lines()
        assertEquals(FrameTimes.HEADER, lines[0])
        assertEquals("0,1002.000,1000000000,1", lines[1])
        assertEquals("1,1018.667,1016666667,0", lines[2])
    }
}
