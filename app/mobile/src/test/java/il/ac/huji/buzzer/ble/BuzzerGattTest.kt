package il.ac.huji.buzzer.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuzzerGattTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun encodesCommandsAsInTheDraft() {
        assertArrayEquals(bytes(0x01, 0xF0, 0x00), BuzzerGatt.startStreaming(240))
        assertArrayEquals(bytes(0x02), BuzzerGatt.stopStreaming())
        assertArrayEquals(bytes(0x10, 0x01, 0x2C, 0x01, 0xFF), BuzzerGatt.vibrate(SensorPoint.FOREARM, 300, 255))
    }

    @Test
    fun rejectsValuesThatDoNotFit() {
        assertThrows(IllegalArgumentException::class.java) { BuzzerGatt.startStreaming(70_000) }
        assertThrows(IllegalArgumentException::class.java) { BuzzerGatt.vibrate(SensorPoint.HAND, 100, 256) }
    }
}
