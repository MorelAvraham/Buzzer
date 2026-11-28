package il.ac.huji.buzzer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ClockSyncTest {

    /**
     * A made-up board clock: it started [startMs] after the phone clock and runs [rate] times
     * as fast. Returns pings with random one-way delays, like a BLE link.
     */
    private fun simulatePings(
        startMs: Double,
        rate: Double,
        count: Int,
        everyMs: Double,
        seed: Int = 1,
    ): List<Ping> {
        val rnd = Random(seed)
        return List(count) { i ->
            val send = 10_000.0 + i * everyMs
            val up = 2.0 + rnd.nextDouble() * 40.0
            val down = 2.0 + rnd.nextDouble() * 40.0
            val boardMs = (send + up - startMs) * rate
            Ping(send, send + up + down, (boardMs * 1000).toLong())
        }
    }

    private fun trueBoardToPhone(boardMs: Double, startMs: Double, rate: Double) = boardMs / rate + startMs

    @Test
    fun noPingsGivesNoMapping() {
        assertNull(ClockSync.fit(emptyList()))
    }

    @Test
    fun onePingGivesTheMidpointOffset() {
        val m = ClockSync.fit(listOf(Ping(1_000.0, 1_020.0, 500_000)))!!
        assertEquals(1_010.0 - 500.0, m.offsetMs, 1e-9)
        assertEquals(1.0, m.rate, 0.0)
        assertEquals(1, m.pingsUsed)
    }

    @Test
    fun picksTheShortestRoundTripInAWindow() {
        val pings = listOf(
            Ping(1_000.0, 1_080.0, 600_000), // long round trip, board time poorly known
            Ping(1_100.0, 1_104.0, 1_100_000),
        )
        val m = ClockSync.fit(pings)!!
        assertEquals(1, m.pingsUsed)
        assertEquals(1_102.0 - 1_100.0, m.offsetMs, 1e-9)
        assertEquals(4.0, m.worstRoundTripMs, 1e-9)
    }

    @Test
    fun followsAClockThatRunsFast() {
        // 100 ppm faster, a pessimistic value for a crystal. Ten minutes of pings, one per second.
        val start = 3_000.0
        val rate = 1.0001
        val m = ClockSync.fit(simulatePings(start, rate, count = 600, everyMs = 1_000.0))!!
        for (boardMs in listOf(0.0, 100_000.0, 300_000.0, 600_000.0)) {
            val err = m.toPhoneMs((boardMs * 1000).toLong()) - trueBoardToPhone(boardMs, start, rate)
            // With up to 40 ms random delay each way, the fitted line should stay within a few ms.
            assertEquals("error at board $boardMs ms", 0.0, err, 3.0)
        }
    }

    @Test
    fun ignoresNegativeRoundTrips() {
        val m = ClockSync.fit(listOf(Ping(1_000.0, 990.0, 1), Ping(2_000.0, 2_010.0, 1_000_000)))!!
        assertEquals(1, m.pingsUsed)
    }
}
