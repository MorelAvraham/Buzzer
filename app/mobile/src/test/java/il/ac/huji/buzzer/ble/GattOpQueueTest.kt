package il.ac.huji.buzzer.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GattOpQueueTest {
    @Test
    fun runsOneOperationAtATime() {
        val q = GattOpQueue()
        val started = mutableListOf<String>()
        q.enqueue("a") { started.add("a"); true }
        q.enqueue("b") { started.add("b"); true }
        assertEquals(listOf("a"), started)
        q.done()
        assertEquals(listOf("a", "b"), started)
        q.done()
        assertFalse(q.isBusy)
    }

    @Test
    fun skipsAnOperationThatDoesNotStart() {
        val q = GattOpQueue()
        val started = mutableListOf<String>()
        q.enqueue("a") { started.add("a"); true }
        q.enqueue("broken") { false }
        q.enqueue("c") { started.add("c"); true }
        q.done()
        assertEquals(listOf("a", "c"), started)
        assertEquals(listOf("broken"), q.failed)
    }

    @Test
    fun clearDropsEverything() {
        val q = GattOpQueue()
        q.enqueue("a") { true }
        q.enqueue("b") { true }
        q.clear()
        assertEquals(0, q.size)
        var ran = false
        q.enqueue("c") { ran = true; true }
        assertEquals(true, ran)
    }
}
