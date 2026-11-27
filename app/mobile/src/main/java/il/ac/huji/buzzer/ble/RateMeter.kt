package il.ac.huji.buzzer.ble

/**
 * Samples per second that really arrived at the phone, over the last [windowMs].
 * Compare it with the rate the board says it sends: a lower value means lost data.
 */
class RateMeter(private val windowMs: Double = 2_000.0) {
    private val arrivals = ArrayDeque<Pair<Double, Int>>()
    private var inWindow = 0

    fun add(phoneMs: Double, samples: Int) {
        arrivals.addLast(phoneMs to samples)
        inWindow += samples
        drop(phoneMs)
    }

    fun rateHz(nowMs: Double): Double {
        drop(nowMs)
        if (arrivals.isEmpty()) return 0.0
        return inWindow * 1000.0 / windowMs
    }

    fun reset() {
        arrivals.clear()
        inWindow = 0
    }

    private fun drop(nowMs: Double) {
        while (arrivals.isNotEmpty() && arrivals.first().first <= nowMs - windowMs) {
            inWindow -= arrivals.removeFirst().second
        }
    }
}
