package fr.makoff.music.playback

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class AutomaticSleepTimerScheduleTest {
    private fun active(time: String, repeat: String = "daily", start: String = "22:00", end: String = "06:00",
        days: String = "0,1,2,3,4", overrides: String = "") = AutomaticSleepTimerSchedule.isActive(
        LocalDateTime.parse(time), repeat, start, end, days, overrides)

    @Test fun dailyWindowIncludesStartAndExcludesEnd() {
        assertTrue(active("2026-10-23T22:00"))
        assertTrue(active("2026-10-24T05:59"))
        assertFalse(active("2026-10-24T06:00"))
        assertFalse(active("2026-10-23T21:59"))
    }

    @Test fun weekdayNightContinuesOnSaturdayButNotMondayMorning() {
        assertTrue(active("2026-10-24T02:00", "weekdays"))
        assertFalse(active("2026-10-26T02:00", "weekdays"))
        assertFalse(active("2026-10-24T23:00", "weekdays"))
    }

    @Test fun weekendNightContinuesOnMonday() {
        assertTrue(active("2026-10-26T02:00", "weekends"))
        assertFalse(active("2026-10-24T02:00", "weekends"))
    }

    @Test fun customNightUsesPreviousDaysOverride() {
        assertTrue(active("2026-10-24T07:00", "custom", days = "4", overrides = "4=23:00-08:00"))
        assertFalse(active("2026-10-24T08:00", "custom", days = "4", overrides = "4=23:00-08:00"))
        assertFalse(active("2026-10-25T07:00", "custom", days = "4", overrides = "4=23:00-08:00"))
    }

    @Test fun daytimeAndCombinedModesUseTheirOwnWindows() {
        assertTrue(active("2026-10-23T12:00", start = "09:00", end = "17:00"))
        assertFalse(active("2026-10-23T18:00", start = "09:00", end = "17:00"))
        assertTrue(active("2026-10-24T12:00", "weekdays_weekends", overrides = "5=10:00-14:00"))
        assertFalse(active("2026-10-24T15:00", "weekdays_weekends", overrides = "5=10:00-14:00"))
        assertFalse(active("2026-10-23T23:00", "unknown"))
    }
}
