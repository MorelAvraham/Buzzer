package il.ac.huji.buzzer.ble

/**
 * Android BLE runs only one GATT operation at a time. A second one started before the first
 * has finished fails without much warning. This queue starts the next operation only after
 * the callback of the current one has called [done].
 *
 * Each operation returns true if it started. If it did not start, the queue moves on.
 * Not thread safe: call it from one thread, or under a lock.
 */
class GattOpQueue {
    private val pending = ArrayDeque<Op>()
    private var running: Op? = null

    private class Op(val name: String, val start: () -> Boolean)

    val isBusy: Boolean get() = running != null
    val size: Int get() = pending.size + if (running != null) 1 else 0

    /** Names of operations that could not start, for the screen. */
    val failed = mutableListOf<String>()

    fun enqueue(name: String, start: () -> Boolean) {
        pending.addLast(Op(name, start))
        if (running == null) next()
    }

    /** Call from the GATT callback that ends the current operation. */
    fun done() {
        running = null
        next()
    }

    fun clear() {
        pending.clear()
        running = null
    }

    private fun next() {
        while (pending.isNotEmpty()) {
            val op = pending.removeFirst()
            running = op
            if (op.start()) return
            failed.add(op.name)
            running = null
        }
    }
}
