package il.ac.huji.buzzer.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class RateMeterTest {
    @Test
    fun measuresSamplesPerSecond() {
        val m = RateMeter(windowMs = 1_000.0)
        // 240 samples per second, in packets of 12 every 50 ms, for 3 s.
        var t = 0.0
        while (t < 3_000.0) {
            m.add(t, 12)
            t += 50.0
        }
        assertEquals(240.0, m.rateHz(t - 50.0), 12.0)
    }

    @Test
    fun dropsToZeroWhenNothingArrives() {
        val m = RateMeter(windowMs = 1_000.0)
        m.add(0.0, 100)
        assertEquals(0.0, m.rateHz(5_000.0), 0.0)
    }
}
