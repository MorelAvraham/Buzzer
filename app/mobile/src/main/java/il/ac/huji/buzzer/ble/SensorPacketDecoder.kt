package il.ac.huji.buzzer.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Thrown when a notification does not match the packet format. */
class PacketFormatException(message: String) : Exception(message)

/** Decodes sensor data packets in the draft format of firmware/README.md. */
object SensorPacketDecoder {
    const val SUPPORTED_VERSION = 1
    const val HEADER_SIZE = 12
    const val SAMPLE_SIZE = 12
    const val MAX_SAMPLES = 19

    fun decode(bytes: ByteArray): SensorPacket {
        if (bytes.size < HEADER_SIZE) {
            throw PacketFormatException("packet has ${bytes.size} bytes, the header alone needs $HEADER_SIZE")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.get().toInt() and 0xFF
        if (version != SUPPORTED_VERSION) {
            throw PacketFormatException("version $version, this app reads version $SUPPORTED_VERSION")
        }
        val pointCode = buf.get().toInt() and 0xFF
        val point = SensorPoint.fromCode(pointCode)
            ?: throw PacketFormatException("unknown sensor point $pointCode")
        val seq = buf.short.toInt() and 0xFFFF
        val t0Us = buf.int.toLong() and 0xFFFF_FFFFL
        val dtUs = buf.short.toInt() and 0xFFFF
        if (dtUs == 0) throw PacketFormatException("dt_us is 0")
        val n = buf.get().toInt() and 0xFF
        if (n !in 1..MAX_SAMPLES) throw PacketFormatException("n is $n, must be 1 to $MAX_SAMPLES")
        val expected = HEADER_SIZE + n * SAMPLE_SIZE
        if (bytes.size != expected) {
            throw PacketFormatException("packet has ${bytes.size} bytes, n = $n needs $expected")
        }
        val flags = buf.get().toInt() and 0xFF
        val accelRange = AccelRange.fromCode(flags and 0b11)
            ?: throw PacketFormatException("unknown accelerometer range in flags $flags")
        val gyroCode = (flags shr 2) and 0b111
        val gyroRange = GyroRange.fromCode(gyroCode)
            ?: throw PacketFormatException("unknown gyroscope range code $gyroCode")
        val samples = List(n) {
            RawSample(buf.short, buf.short, buf.short, buf.short, buf.short, buf.short)
        }
        return SensorPacket(version, point, seq, t0Us, dtUs, accelRange, gyroRange, samples)
    }
}
