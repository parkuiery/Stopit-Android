package com.uiery.keep.util

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime as JavaLocalTime
import java.util.Locale

class TimeExtTest {

    @Test
    fun formatAmPmTimeUsesAsciiDigitsEvenWhenDefaultLocaleUsesLocalizedDigits() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        try {
            assertEquals("오전 09:05", formatAmPmTime(amPm = "오전", hour24 = 9, minute = 5))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatAmPmTimeConvertsMidnightAndAfternoonToTwelveHourClock() {
        assertEquals("AM 12:00", formatAmPmTime(amPm = "AM", hour24 = 0, minute = 0))
        assertEquals("PM 01:07", formatAmPmTime(amPm = "PM", hour24 = 13, minute = 7))
    }

    @Test
    fun formatTwentyFourHourTimeUsesAsciiDigitsEvenWhenDefaultLocaleUsesLocalizedDigits() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        try {
            assertEquals("08:05", formatTwentyFourHourTime(JavaLocalTime.of(8, 5)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatMonthDayUsesAsciiDigitsEvenWhenDefaultLocaleUsesLocalizedDigits() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        try {
            assertEquals("5.2", formatMonthDay(LocalDate.of(2026, 5, 2)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatYearMonthUsesAsciiDigitsEvenWhenDefaultLocaleUsesLocalizedDigits() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        try {
            assertEquals("2026.5", formatYearMonth(LocalDate.of(2026, 5, 2)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatLockHistoryDateHeaderUsesLocalizedWeekdayWithAsciiDigits() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        try {
            assertEquals(
                "5/2 (토)",
                formatLockHistoryDateHeader(
                    date = LocalDate.of(2026, 5, 2),
                    locale = Locale.KOREAN,
                ),
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun routineDurationMinutesHandlesSameDayAndCrossMidnight() {
        assertEquals(
            60,
            routineDurationMinutes(
                startTime = LocalTime(hour = 9, minute = 0),
                endTime = LocalTime(hour = 10, minute = 0),
            ),
        )
        assertEquals(
            45,
            routineDurationMinutes(
                startTime = LocalTime(hour = 23, minute = 30),
                endTime = LocalTime(hour = 0, minute = 15),
            ),
        )
    }

    @Test
    fun isRoutineActiveNowHandlesSameDayWindow() {
        val repeatDays = listOf(DayOfWeek.WEDNESDAY)

        assertTrue(
            isRoutineActiveNow(
                startTime = LocalTime(hour = 9, minute = 0),
                endTime = LocalTime(hour = 10, minute = 0),
                repeatDays = repeatDays,
                nowDateTime = LocalDateTime.of(2026, 4, 29, 9, 30),
            ),
        )
        assertFalse(
            isRoutineActiveNow(
                startTime = LocalTime(hour = 9, minute = 0),
                endTime = LocalTime(hour = 10, minute = 0),
                repeatDays = repeatDays,
                nowDateTime = LocalDateTime.of(2026, 4, 29, 10, 0),
            ),
        )
    }

    @Test
    fun isRoutineActiveNowHandlesCrossMidnightWindow() {
        val repeatDays = listOf(DayOfWeek.MONDAY)

        assertTrue(
            isRoutineActiveNow(
                startTime = LocalTime(hour = 23, minute = 0),
                endTime = LocalTime(hour = 1, minute = 0),
                repeatDays = repeatDays,
                nowDateTime = LocalDateTime.of(2026, 4, 27, 23, 30),
            ),
        )
        assertTrue(
            isRoutineActiveNow(
                startTime = LocalTime(hour = 23, minute = 0),
                endTime = LocalTime(hour = 1, minute = 0),
                repeatDays = repeatDays,
                nowDateTime = LocalDateTime.of(2026, 4, 28, 0, 30),
            ),
        )
        assertFalse(
            isRoutineActiveNow(
                startTime = LocalTime(hour = 23, minute = 0),
                endTime = LocalTime(hour = 1, minute = 0),
                repeatDays = repeatDays,
                nowDateTime = LocalDateTime.of(2026, 4, 28, 1, 0),
            ),
        )
    }

    @Test
    fun currentRoutineWindowEndDateTimeHandlesCrossMidnightEnd() {
        assertEquals(
            LocalDateTime.of(2026, 4, 28, 1, 0),
            currentRoutineWindowEndDateTime(
                startTime = LocalTime(hour = 23, minute = 0),
                endTime = LocalTime(hour = 1, minute = 0),
                nowDateTime = LocalDateTime.of(2026, 4, 27, 23, 30),
            ),
        )
    }

    @Test
    fun isRoutineChangeLockedHonorsLockWindowAndEnabledState() {
        val repeatDays = listOf(DayOfWeek.MONDAY)

        assertTrue(
            isRoutineChangeLocked(
                startTime = LocalTime(hour = 10, minute = 0),
                repeatDays = repeatDays,
                changeLockHours = 2,
                isEnabled = true,
                now = LocalDateTime.of(2026, 4, 27, 8, 30),
            ),
        )
        assertFalse(
            isRoutineChangeLocked(
                startTime = LocalTime(hour = 10, minute = 0),
                repeatDays = repeatDays,
                changeLockHours = 2,
                isEnabled = true,
                now = LocalDateTime.of(2026, 4, 27, 7, 59),
            ),
        )
        assertFalse(
            isRoutineChangeLocked(
                startTime = LocalTime(hour = 10, minute = 0),
                repeatDays = repeatDays,
                changeLockHours = 2,
                isEnabled = false,
                now = LocalDateTime.of(2026, 4, 27, 8, 30),
            ),
        )
    }
}
