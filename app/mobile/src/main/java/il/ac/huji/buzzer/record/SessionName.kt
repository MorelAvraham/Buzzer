package il.ac.huji.buzzer.record

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The name of one recording session, as in data/README.md:
 * `<date>_<player>_<session>_<kind>.<ext>`, for example `20261201_P01_S01_video.mp4`.
 *
 * The player is a code like P01, never a name.
 */
data class SessionName(val date: LocalDate, val player: String, val session: Int) {
    init {
        require(isValidPlayer(player)) { "player must be a code like P01, not \"$player\"" }
        require(session in 1..99) { "session must be 1 to 99" }
    }

    val base: String get() = "${date.format(DATE)}_${player}_S${String.format(Locale.ROOT, "%02d", session)}"

    fun file(kind: String, ext: String) = "${base}_$kind.$ext"

    companion object {
        private val DATE = DateTimeFormatter.BASIC_ISO_DATE.withLocale(Locale.ROOT)
        private val PLAYER = Regex("P\\d{2}")
        private val FOLDER = Regex("(\\d{8})_(P\\d{2})_S(\\d{2})")

        fun isValidPlayer(code: String) = PLAYER.matches(code)

        /** Turns what was typed ("p1", " P07 ") into a code, or null if it cannot be one. */
        fun normalisePlayer(typed: String): String? {
            val digits = typed.trim().uppercase().removePrefix("P")
            val n = digits.toIntOrNull() ?: return null
            if (n !in 1..99 || digits.length > 2) return null
            return String.format(Locale.ROOT, "P%02d", n)
        }

        /** The next free session number for this player and date, given the existing folder names. */
        fun next(date: LocalDate, player: String, existingFolders: Collection<String>): SessionName {
            val day = date.format(DATE)
            val used = existingFolders.mapNotNull { FOLDER.matchEntire(it) }
                .filter { it.groupValues[1] == day && it.groupValues[2] == player }
                .map { it.groupValues[3].toInt() }
            return SessionName(date, player, (used.maxOrNull() ?: 0) + 1)
        }
    }
}
