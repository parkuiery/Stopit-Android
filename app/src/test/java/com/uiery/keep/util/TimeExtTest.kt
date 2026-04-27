package com.uiery.keep.util

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class TimeExtTest {

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
