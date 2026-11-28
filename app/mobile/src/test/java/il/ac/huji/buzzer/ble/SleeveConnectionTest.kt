package il.ac.huji.buzzer.ble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Feeds notifications straight into SleeveConnection, as the GATT callback would. */
@RunWith(RobolectricTestRunner::class)
class SleeveConnectionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Recorder : SleeveListener {
        val packets = mutableListOf<SensorPacket>()
        val pings = mutableListOf<Triple<Double, Double, Long>>()
        override fun onPacket(packet: SensorPacket, phoneMs: Double) {
            packets.add(packet)
        }
        override fun onPing(phoneSendMs: Double, phoneReceiveMs: Double, boardUsRaw: Long) {
            pings.add(Triple(phoneSendMs, phoneReceiveMs, boardUsRaw))
        }
    }

    @Test
    fun countsPacketsAndPassesThemOn() {
        val c = SleeveConnection(context)
        val rec = Recorder()
        c.listener = rec
        for ((i, seq) in listOf(0, 1, 3).withIndex()) {
            c.onNotification(BuzzerGatt.SENSOR_DATA, TestPackets.encode(seq = seq, n = 10), 1_000.0 + i * 40)
        }
        val s = c.state.value
        assertEquals(3, s.received)
        assertEquals(1, s.lost)
        assertEquals(3, rec.packets.size)
        assertEquals(239.98, s.nominalRateHz!!, 0.01)
    }

    @Test
    fun countsBadPacketsWithoutPassingThemOn() {
        val c = SleeveConnection(context)
        val rec = Recorder()
        c.listener = rec
        c.onNotification(BuzzerGatt.SENSOR_DATA, byteArrayOf(9, 9, 9), 0.0)
        assertEquals(1, c.state.value.badPackets)
        assertEquals(0, rec.packets.size)
    }

    @Test
    fun matchesAPingReplyToItsRequest() {
        val c = SleeveConnection(context)
        val rec = Recorder()
        c.listener = rec
        val echo = c.rememberPing(5_000.25)
        val reply = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x20).putInt(echo.toInt()).putInt(123_456).array()
        c.onNotification(BuzzerGatt.STATUS, reply, 5_012.25)
        assertEquals(listOf(Triple(5_000.25, 5_012.25, 123_456L)), rec.pings)
        assertEquals(12.0, c.state.value.lastRoundTripMs!!, 1e-9)

        // The same reply again has no request left to match.
        c.onNotification(BuzzerGatt.STATUS, reply, 5_020.0)
        assertEquals(1, rec.pings.size)
    }
}
