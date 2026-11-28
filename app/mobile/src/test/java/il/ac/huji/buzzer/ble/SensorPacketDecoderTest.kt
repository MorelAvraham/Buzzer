package il.ac.huji.buzzer.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SensorPacketDecoderTest {

    /** A packet written byte by byte from the table in firmware/README.md. */
    private val handMadePacket = bytes(
        0x01,                   // version 1
        0x00,                   // point 0, hand
        0x34, 0x12,             // seq 0x1234
        0xEF, 0xCD, 0xAB, 0x89, // t0_us 0x89ABCDEF, above 2^31 to catch sign errors
        0x47, 0x10,             // dt_us 0x1047 = 4167 us, about 240 Hz
        0x02,                   // n = 2 samples
        0x12,                   // flags: accel code 2 (8 g), gyro code 4 (2000 dps)
        // sample 0: ax 1000, ay -1000, az 4096, gx -1, gy 32767, gz -32768
        0xE8, 0x03, 0x18, 0xFC, 0x00, 0x10, 0xFF, 0xFF, 0xFF, 0x7F, 0x00, 0x80,
        // sample 1: ax 1, the rest 0
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun decodesHeader() {
        val p = SensorPacketDecoder.decode(handMadePacket)
        assertEquals(1, p.version)
        assertEquals(SensorPoint.HAND, p.point)
        assertEquals(0x1234, p.seq)
        assertEquals(0x89ABCDEFL, p.t0Us)
        assertEquals(4167, p.dtUs)
        assertEquals(AccelRange.G8, p.accelRange)
        assertEquals(GyroRange.DPS2000, p.gyroRange)
        assertEquals(2, p.samples.size)
    }

    @Test
    fun decodesSignedSamples() {
        val p = SensorPacketDecoder.decode(handMadePacket)
        assertEquals(RawSample(1000, -1000, 4096, -1, 32767, -32768), p.samples[0])
        assertEquals(RawSample(1, 0, 0, 0, 0, 0), p.samples[1])
    }

    @Test
    fun sampleTimesFollowDt() {
        val p = SensorPacketDecoder.decode(handMadePacket)
        assertEquals(0x89ABCDEFL, p.sampleTimeUs(0))
        assertEquals(0x89ABCDEFL + 4167, p.sampleTimeUs(1))
        assertEquals(239.98, p.nominalRateHz, 0.01)
    }

    @Test
    fun scalesToPhysicalUnits() {
        val s = SensorPacketDecoder.decode(handMadePacket).scaled(0)
        // 4096 LSB x 0.244 mg = 999.424 mg = 9.8010 m/s²
        assertEquals(9.8010, s.az, 0.0001)
        // -32768 LSB x 70 mdps = -2293.76 dps
        assertEquals(-2293.76, s.gz, 0.001)
        assertEquals(-0.07, s.gx, 1e-9)
    }

    @Test
    fun readsEveryPointAndRange() {
        for (point in SensorPoint.entries) {
            for (accel in AccelRange.entries) {
                for (gyro in GyroRange.entries) {
                    val p = SensorPacketDecoder.decode(
                        TestPackets.encode(point = point, accel = accel, gyro = gyro),
                    )
                    assertEquals(point, p.point)
                    assertEquals(accel, p.accelRange)
                    assertEquals(gyro, p.gyroRange)
                }
            }
        }
    }

    @Test
    fun acceptsTheLargestPacket() {
        val p = SensorPacketDecoder.decode(TestPackets.encode(n = 19))
        assertEquals(19, p.samples.size)
    }

    @Test
    fun rejectsBadPackets() {
        val good = handMadePacket
        fun changed(index: Int, value: Int) = good.copyOf().also { it[index] = value.toByte() }

        val bad = mapOf(
            "too short" to good.copyOf(11),
            "version 2" to changed(0, 2),
            "point 3" to changed(1, 3),
            "n is 0" to changed(10, 0),
            "n is 20" to changed(10, 20),
            "n does not match the length" to changed(10, 3),
            "gyro code 6" to changed(11, 0b11000),
            "dt is 0" to good.copyOf().also { it[8] = 0; it[9] = 0 },
            "one byte too many" to good.copyOf(good.size + 1),
        )
        for ((name, packet) in bad) {
            assertThrows(name, PacketFormatException::class.java) { SensorPacketDecoder.decode(packet) }
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
