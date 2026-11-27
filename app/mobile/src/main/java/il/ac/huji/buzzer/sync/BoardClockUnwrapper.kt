package il.ac.huji.buzzer.sync

/**
 * The board sends its time as a 32-bit microsecond counter, which wraps after about 71 minutes.
 * This class turns those values into one time line that keeps growing.
 *
 * Values can come a little out of order (packet start times and ping replies are interleaved),
 * so a jump back of less than half the counter range is treated as "a bit earlier", not as a wrap.
 */
class BoardClockUnwrapper {
    private var wraps = 0L
    private var last: Long? = null

    /** [raw] is 0 to 2^32 - 1. Returns microseconds on the continuous board time line. */
    fun unwrap(raw: Long): Long {
        require(raw in 0..MAX) { "board time $raw is not a 32-bit value" }
        val prev = last
        if (prev == null) {
            last = raw
            return raw
        }
        var epoch = wraps
        val diff = raw - prev
        if (diff < -HALF) {
            // Forward across the wrap.
            wraps++
            epoch = wraps
            last = raw
        } else if (diff > HALF) {
            // A late value from before the last wrap.
            epoch = wraps - 1
        } else if (diff > 0) {
            last = raw
        }
        return epoch * RANGE + raw
    }

    companion object {
        const val RANGE = 1L shl 32
        const val MAX = RANGE - 1
        const val HALF = RANGE / 2
    }
}
