package il.ac.huji.buzzer.sync

/**
 * One time ping: the phone sends its time, the board answers with its own time.
 * The board time was read somewhere between [phoneSendMs] and [phoneReceiveMs].
 */
data class Ping(
    val phoneSendMs: Double,
    val phoneReceiveMs: Double,
    /** Board time in microseconds, already unwrapped. */
    val boardUs: Long,
) {
    val roundTripMs: Double get() = phoneReceiveMs - phoneSendMs
    val phoneMidMs: Double get() = (phoneSendMs + phoneReceiveMs) / 2
    val boardMs: Double get() = boardUs / 1000.0
}

/** phone ms = [offsetMs] + [rate] x board ms. */
data class ClockMapping(
    val offsetMs: Double,
    val rate: Double,
    /** Pings used for the fit, one per window. */
    val pingsUsed: Int,
    /** Largest round trip among the pings used. The error of one ping is at most half of it. */
    val worstRoundTripMs: Double,
) {
    fun toPhoneMs(boardUs: Long): Double = offsetMs + rate * (boardUs / 1000.0)
}

/**
 * Finds the mapping from board time to phone time from the pings of one session.
 *
 * In each window of [windowMs] it keeps the ping with the shortest round trip, because a short
 * round trip means the board time is known most exactly. With one window it uses a constant
 * offset. With more it fits a straight line, which also corrects a small difference in clock speed.
 *
 * This assumes the delay to the board and back is about the same in both directions.
 */
object ClockSync {
    fun fit(pings: List<Ping>, windowMs: Double = 5_000.0): ClockMapping? {
        val valid = pings.filter { it.roundTripMs >= 0 }
        if (valid.isEmpty()) return null
        val start = valid.minOf { it.phoneSendMs }
        val best = valid
            .groupBy { ((it.phoneSendMs - start) / windowMs).toLong() }
            .values
            .map { group -> group.minBy { it.roundTripMs } }
            .sortedBy { it.boardUs }
        val worst = best.maxOf { it.roundTripMs }
        if (best.size == 1) {
            val p = best[0]
            return ClockMapping(p.phoneMidMs - p.boardMs, 1.0, 1, worst)
        }
        // Least squares line, phone = a + b x board. Centred to keep the numbers small.
        val mx = best.map { it.boardMs }.average()
        val my = best.map { it.phoneMidMs }.average()
        var sxy = 0.0
        var sxx = 0.0
        for (p in best) {
            val dx = p.boardMs - mx
            sxy += dx * (p.phoneMidMs - my)
            sxx += dx * dx
        }
        if (sxx == 0.0) {
            return ClockMapping(my - mx, 1.0, best.size, worst)
        }
        val b = sxy / sxx
        return ClockMapping(my - b * mx, b, best.size, worst)
    }
}
