package fr.bastien.assistant.ui.main

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLabelTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val now = millis(2026, 10, 12, 11, 15)

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun today() {
        assertEquals("Aujourd'hui 09:05", TimeLabel.format(millis(2026, 10, 12, 9, 5), now, paris))
    }

    @Test
    fun yesterday() {
        assertEquals("Hier 23:40", TimeLabel.format(millis(2026, 10, 11, 23, 40), now, paris))
    }

    @Test
    fun earlierThisYearShowsTheWeekday() {
        val label = TimeLabel.format(millis(2026, 10, 5, 14, 32), now, paris)
        assertEquals(true, label.startsWith("lun"))
        assertEquals(true, label.endsWith("5 oct. 14:32"))
    }

    @Test
    fun anotherYearShowsTheYear() {
        val label = TimeLabel.format(millis(2025, 12, 31, 8, 0), now, paris)
        assertEquals(true, label.endsWith("2025 08:00"))
    }
}
