package fr.makoff.music.playback

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal object AutomaticSleepTimerSchedule {
    fun isActive(
        now: LocalDateTime,
        repeat: String,
        start: String,
        end: String,
        customDays: String,
        dayTimes: String,
    ): Boolean {
        val selectedDays = customDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        val overrides = dayTimes.split(';').mapNotNull { entry ->
            val parts = entry.split('=')
            val day = parts.firstOrNull()?.toIntOrNull()
            val times = parts.getOrNull(1)?.split('-')
            if (day == null || parts.size != 2 || times == null || times.size != 2) null
            else day to (times[0] to times[1])
        }.toMap()
        fun allowed(date: LocalDate): Boolean = when (repeat) {
            "daily", "weekdays_weekends" -> true
            "weekdays" -> date.dayOfWeek.value <= 5
            "weekends" -> date.dayOfWeek.value >= 6
            "custom" -> date.dayOfWeek.value - 1 in selectedDays
            else -> false
        }
        // The early morning part belongs to the previous day's overnight window.
        return listOf(now.toLocalDate(), now.toLocalDate().minusDays(1)).any { date ->
            if (!allowed(date)) return@any false
            val times = if (repeat == "daily") start to end else overrides[date.dayOfWeek.value - 1] ?: (start to end)
            val from = LocalTime.parse(times.first)
            val until = LocalTime.parse(times.second)
            val begin = date.atTime(from)
            val finish = (if (until.isAfter(from)) date else date.plusDays(1)).atTime(until)
            !now.isBefore(begin) && now.isBefore(finish)
        }
    }
}
