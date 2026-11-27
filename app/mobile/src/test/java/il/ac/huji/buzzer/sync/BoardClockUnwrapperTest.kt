package il.ac.huji.buzzer.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardClockUnwrapperTest {
    private val range = BoardClockUnwrapper.RANGE

    @Test
    fun keepsValuesBeforeAnyWrap() {
        val u = BoardClockUnwrapper()
        assertEquals(100L, u.unwrap(100))
        assertEquals(5_000L, u.unwrap(5_000))
    }

    @Test
    fun continuesAcrossTheWrap() {
        val u = BoardClockUnwrapper()
        assertEquals(range - 10, u.unwrap(range - 10))
        assertEquals(range + 5, u.unwrap(5))
        assertEquals(range + 1_000, u.unwrap(1_000))
    }

    @Test
    fun lateValueFromBeforeTheWrapStaysBefore() {
        val u = BoardClockUnwrapper()
        u.unwrap(range - 100)
        u.unwrap(50)
        assertEquals(range - 80, u.unwrap(range - 80))
        assertEquals(range + 60, u.unwrap(60))
    }

    @Test
    fun slightlyEarlierValueIsNotAWrap() {
        val u = BoardClockUnwrapper()
        u.unwrap(10_000)
        assertEquals(9_000L, u.unwrap(9_000))
        assertEquals(11_000L, u.unwrap(11_000))
    }

    @Test
    fun handlesManyWraps() {
        val u = BoardClockUnwrapper()
        var expected = 0L
        val step = range / 3
        repeat(10) {
            assertEquals(expected, u.unwrap(expected % range))
            expected += step
        }
    }

    @Test
    fun rejectsValuesOutside32Bits() {
        val u = BoardClockUnwrapper()
        assertThrows(IllegalArgumentException::class.java) { u.unwrap(-1) }
        assertThrows(IllegalArgumentException::class.java) { u.unwrap(range) }
    }
}
