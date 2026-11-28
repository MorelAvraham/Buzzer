package il.ac.huji.buzzer.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** GATT UUIDs and control commands of the draft BLE format in firmware/README.md. */
object BuzzerGatt {
    val SERVICE: UUID = UUID.fromString("a0e80001-f9e8-48f1-abb4-2eccab5b865a")
    val SENSOR_DATA: UUID = UUID.fromString("a0e80002-f9e8-48f1-abb4-2eccab5b865a")
    val CONTROL: UUID = UUID.fromString("a0e80003-f9e8-48f1-abb4-2eccab5b865a")
    val STATUS: UUID = UUID.fromString("a0e80004-f9e8-48f1-abb4-2eccab5b865a")

    /** Standard descriptor that turns notifications on and off. */
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** MTU the phone asks for, so that one packet can hold 19 samples. */
    const val MTU = 247

    fun startStreaming(rateHz: Int): ByteArray {
        require(rateHz in 1..0xFFFF) { "rate $rateHz Hz does not fit in u16" }
        return ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x01).putShort(rateHz.toShort()).array()
    }

    fun stopStreaming(): ByteArray = byteArrayOf(0x02)

    fun vibrate(point: SensorPoint, durationMs: Int, strength: Int): ByteArray {
        require(durationMs in 0..0xFFFF) { "duration $durationMs ms does not fit in u16" }
        require(strength in 0..255) { "strength must be 0 to 255" }
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x10).put(point.code.toByte()).putShort(durationMs.toShort()).put(strength.toByte())
            .array()
    }
}
