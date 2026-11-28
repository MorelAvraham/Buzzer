package il.ac.huji.buzzer.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMetaTest {
    private val meta = SessionMeta(
        session = "20261201_P01_S01",
        player = "P01",
        startedAtUtc = "2026-12-01T10:00:00Z",
        phoneModel = "Test Phone",
        androidVersion = "16",
        shotType = "free throw",
        plannedShots = 50,
        cameraSide = "right",
        cameraDistanceM = null,
        notes = "line 1\nsaid \"ok\"",
        appVersion = "0.1",
        frameRate = 60,
        timestampSource = "REALTIME",
        videoFrames = 3600,
        matchedFrames = 3598,
        sleeveAddress = null,
        sensorRateHz = 240,
        sensorSamples = 14400,
        packetsReceived = 1200,
        packetsLost = 3,
        clockPings = 60,
        clockOffsetMs = 1234.5,
        clockRate = 1.00001,
        clockWorstRoundTripMs = 18.0,
    )

    @Test
    fun writesEveryFieldWithUnknownsAsNull() {
        val json = meta.toJson()
        assertTrue(json.contains("\"player\": \"P01\""))
        assertTrue(json.contains("\"camera_distance_m\": null"))
        assertTrue(json.contains("\"firmware_version\": null"))
        assertTrue(json.contains("\"frame_rate\": 60"))
        assertTrue(json.contains("\"clock_offset_ms\": 1234.500000"))
        assertEquals(26, Regex("^  \"", RegexOption.MULTILINE).findAll(json).count())
    }

    @Test
    fun escapesNotes() {
        assertTrue(meta.toJson().contains("\"notes\": \"line 1\\nsaid \\\"ok\\\"\""))
    }
}
