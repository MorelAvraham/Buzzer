package il.ac.huji.buzzer.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds packets in the draft format for tests. */
object TestPackets {
    fun encode(
        seq: Int = 0,
        point: SensorPoint = SensorPoint.HAND,
        t0Us: Long = 0,
        dtUs: Int = 4167,
        n: Int = 1,
        accel: AccelRange = AccelRange.G8,
        gyro: GyroRange = GyroRange.DPS2000,
    ): ByteArray {
        val buf = ByteBuffer.allocate(12 + 12 * n).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(1)
        buf.put(point.code.toByte())
        buf.putShort(seq.toShort())
        buf.putInt(t0Us.toInt())
        buf.putShort(dtUs.toShort())
        buf.put(n.toByte())
        buf.put((accel.code or (gyro.code shl 2)).toByte())
        repeat(n * 6) { buf.putShort(it.toShort()) }
        return buf.array()
    }

    fun packet(seq: Int, n: Int = 1) = SensorPacketDecoder.decode(encode(seq = seq, n = n))
}
