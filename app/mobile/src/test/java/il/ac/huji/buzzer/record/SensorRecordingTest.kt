package il.ac.huji.buzzer.record

import il.ac.huji.buzzer.ble.SensorPacketDecoder
import il.ac.huji.buzzer.ble.SensorPoint
import il.ac.huji.buzzer.ble.TestPackets
import il.ac.huji.buzzer.ble.TimePing
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringWriter
import java.util.Locale

class SensorRecordingTest {
    private val savedLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun packet(seq: Int, t0Us: Long, n: Int = 2, point: SensorPoint = SensorPoint.HAND) =
        SensorPacketDecoder.decode(TestPackets.encode(seq = seq, t0Us = t0Us, n = n, point = point, dtUs = 1_000))

    @Test
    fun writesNothingWithoutAPing() {
        val rec = SensorRecording(SensorPoint.HAND)
        rec.addPacket(packet(0, 0))
        val out = StringWriter()
        assertNull(rec.writeCsv(out))
        assertEquals("", out.toString())
    }

    @Test
    fun writesHeaderAndOneRowPerSampleOnThePhoneClock() {
        val rec = SensorRecording(SensorPoint.HAND)
        rec.addPacket(packet(0, t0Us = 2_000_000))
        rec.addPacket(packet(1, t0Us = 2_002_000))
        // Board time 1 000 000 us was read between phone 5 000 and 5 010 ms: offset 4 005 ms.
        rec.addPing(5_000.0, 5_010.0, 1_000_000)
        val out = StringWriter()
        rec.writeCsv(out)
        val lines = out.toString().trim().lines()
        assertEquals(SensorRecording.HEADER, lines[0])
        assertEquals(5, lines.size)
        val times = lines.drop(1).map { it.split(",")[0].toDouble() }
        assertArrayEquals(doubleArrayOf(6_005.0, 6_006.0, 6_007.0, 6_008.0), times.toDoubleArray(), 1e-6)
        assertEquals("2000000", lines[1].split(",")[7])
    }

    @Test
    fun usesADecimalPointWhateverTheLanguage() {
        Locale.setDefault(Locale.GERMANY) // German uses a decimal comma
        val rec = SensorRecording(SensorPoint.HAND)
        rec.addPacket(packet(0, t0Us = 0, n = 1))
        rec.addPing(0.0, 1.0, 0)
        val out = StringWriter()
        rec.writeCsv(out)
        val row = out.toString().trim().lines()[1]
        assertEquals(8, row.split(",").size)
        assertEquals("0.500", row.split(",")[0])
    }

    @Test
    fun ignoresOtherSensorPoints() {
        val rec = SensorRecording(SensorPoint.HAND)
        rec.addPacket(packet(0, 0, point = SensorPoint.FOREARM))
        assertEquals(0, rec.sampleCount)
    }

    @Test
    fun keepsTimeGoingAcrossTheBoardClockWrap() {
        val rec = SensorRecording(SensorPoint.HAND)
        val nearWrap = (1L shl 32) - 1_000
        rec.addPacket(packet(0, t0Us = nearWrap, n = 1))
        rec.addPacket(packet(1, t0Us = 500, n = 1))
        rec.addPing(0.0, 0.0, nearWrap)
        val out = StringWriter()
        rec.writeCsv(out)
        val times = out.toString().trim().lines().drop(1).map { it.split(",")[0].toDouble() }
        assertEquals(1.5, times[1] - times[0], 1e-6)
    }

    @Test
    fun timePingRoundTrip() {
        val req = TimePing.encodeRequest(0x1_0000_0005L)
        assertEquals(5, req.size)
        assertEquals(0x20.toByte(), req[0])
        assertEquals(5.toByte(), req[1])
        val reply = byteArrayOf(0x20, 5, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(TimePing.Reply(5, 0xFFFF_FFFFL), TimePing.decodeReply(reply))
        assertNull(TimePing.decodeReply(reply.copyOf(8)))
        assertNull(TimePing.decodeReply(byteArrayOf(0x21, 0, 0, 0, 0, 0, 0, 0, 0)))
    }
}
