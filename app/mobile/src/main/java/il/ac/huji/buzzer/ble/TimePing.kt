package il.ac.huji.buzzer.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The time ping in the draft BLE format (firmware/README.md).
 *
 * Phone to board, on Control: 0x20, u32 phone time in ms.
 * Board to phone, on Status: 0x20, u32 the same phone time, u32 board time in microseconds.
 */
object TimePing {
    const val OPCODE: Byte = 0x20
    const val REPLY_SIZE = 9

    /** The phone time is sent modulo 2^32 and only used to match the reply to the request. */
    fun encodeRequest(phoneMs: Long): ByteArray =
        ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(OPCODE)
            .putInt(phoneMs.toInt())
            .array()

    data class Reply(val phoneMsEcho: Long, val boardUs: Long)

    /** Returns null if [bytes] is not a time ping reply. */
    fun decodeReply(bytes: ByteArray): Reply? {
        if (bytes.size != REPLY_SIZE || bytes[0] != OPCODE) return null
        val buf = ByteBuffer.wrap(bytes, 1, 8).order(ByteOrder.LITTLE_ENDIAN)
        val echo = buf.int.toLong() and 0xFFFF_FFFFL
        val board = buf.int.toLong() and 0xFFFF_FFFFL
        return Reply(echo, board)
    }
}
