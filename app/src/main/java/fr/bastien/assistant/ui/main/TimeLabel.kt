package fr.bastien.assistant.ui.main

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** « Aujourd'hui 14:32 », « Hier 09:10 », « lun. 12 oct. 14:32 », « 12 oct. 2025 14:32 ». */
object TimeLabel {
    private val hour = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    private val sameYear = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.FRENCH)
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.FRENCH)

    fun format(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = moment.toLocalDate()
        return when {
            day == today -> "Aujourd'hui ${moment.format(hour)}"
            day == today.minusDays(1) -> "Hier ${moment.format(hour)}"
            day.year == today.year -> moment.format(sameYear)
            else -> moment.format(otherYear)
        }
    }
}
