package il.ac.huji.buzzer.ble

import il.ac.huji.buzzer.ble.TestPackets.packet
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketStatsTest {

    private fun statsFor(vararg seqs: Int) = PacketStats().apply { seqs.forEach { add(packet(it)) } }

    @Test
    fun noGapNoLoss() {
        val s = statsFor(0, 1, 2, 3)
        assertEquals(4, s.received)
        assertEquals(0, s.lost)
    }

    @Test
    fun countsAGap() {
        val s = statsFor(0, 1, 2, 5)
        assertEquals(2, s.lost)
        assertEquals(2.0 / 6, s.lostShare, 1e-9)
    }

    @Test
    fun handlesWrapAt65536() {
        assertEquals(0, statsFor(65534, 65535, 0, 1).lost)
        assertEquals(2, statsFor(65535, 2).lost)
    }

    @Test
    fun repeatedOrLatePacketIsNotLoss() {
        val s = statsFor(10, 11, 11, 9, 12)
        assertEquals(0, s.lost)
        assertEquals(2, s.outOfOrder)
        assertEquals(5, s.received)
    }

    @Test
    fun countsSamples() {
        val s = PacketStats()
        s.add(packet(0, n = 19))
        s.add(packet(1, n = 5))
        assertEquals(24, s.samples)
    }

    @Test
    fun resetStartsOver() {
        val s = statsFor(0, 5)
        s.reset()
        s.add(packet(100))
        assertEquals(1, s.received)
        assertEquals(0, s.lost)
    }
}
