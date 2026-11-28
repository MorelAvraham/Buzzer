package il.ac.huji.buzzer.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SessionNameTest {
    private val day = LocalDate.of(2026, 12, 1)

    @Test
    fun followsTheNamingScheme() {
        val n = SessionName(day, "P01", 1)
        assertEquals("20261201_P01_S01", n.base)
        assertEquals("20261201_P01_S01_video.mp4", n.file("video", "mp4"))
        assertEquals("20261201_P01_S01_hand.csv", n.file("hand", "csv"))
    }

    @Test
    fun usesLatinDigitsWhateverTheLanguage() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG")) // Arabic digits by default
            assertEquals("20261201_P03_S02", SessionName(day, "P03", 2).base)
            assertEquals("P04", SessionName.normalisePlayer("4"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun refusesNamesAndBadNumbers() {
        assertThrows(IllegalArgumentException::class.java) { SessionName(day, "Morel", 1) }
        assertThrows(IllegalArgumentException::class.java) { SessionName(day, "P1", 1) }
        assertThrows(IllegalArgumentException::class.java) { SessionName(day, "P01", 0) }
        assertThrows(IllegalArgumentException::class.java) { SessionName(day, "P01", 100) }
    }

    @Test
    fun normalisesWhatWasTyped() {
        assertEquals("P01", SessionName.normalisePlayer("p1"))
        assertEquals("P07", SessionName.normalisePlayer(" P07 "))
        assertEquals("P12", SessionName.normalisePlayer("12"))
        assertNull(SessionName.normalisePlayer("Eitan"))
        assertNull(SessionName.normalisePlayer("P0"))
        assertNull(SessionName.normalisePlayer("P123"))
    }

    @Test
    fun nextSessionCountsOnlyThisPlayerAndDay() {
        val existing = listOf(
            "20261201_P01_S01",
            "20261201_P01_S03",
            "20261201_P02_S07",
            "20261130_P01_S09",
            "videotest_20261201_101010",
        )
        assertEquals(4, SessionName.next(day, "P01", existing).session)
        assertEquals(8, SessionName.next(day, "P02", existing).session)
        assertEquals(1, SessionName.next(day, "P03", existing).session)
    }
}
