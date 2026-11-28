package il.ac.huji.buzzer.ble

/**
 * Counts received and lost packets from the `seq` counter, which wraps at 65536.
 *
 * A jump forward of up to half the counter range counts as lost packets.
 * A larger jump means a repeated or late packet, which is counted separately.
 */
class PacketStats {
    var received: Long = 0
        private set
    var lost: Long = 0
        private set
    var outOfOrder: Long = 0
        private set
    var samples: Long = 0
        private set

    private var lastSeq: Int? = null

    fun add(packet: SensorPacket) {
        received++
        samples += packet.samples.size
        val last = lastSeq
        if (last != null) {
            val step = (packet.seq - last) and 0xFFFF
            when {
                step == 0 || step > 0x8000 -> {
                    outOfOrder++
                    return
                }
                else -> lost += step - 1
            }
        }
        lastSeq = packet.seq
    }

    /** Lost packets as a share of all packets that should have arrived, 0 to 1. */
    val lostShare: Double
        get() = if (received + lost == 0L) 0.0 else lost.toDouble() / (received + lost)

    fun reset() {
        received = 0
        lost = 0
        outOfOrder = 0
        samples = 0
        lastSeq = null
    }
}
