package com.uiery.keep.feature.home.component

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerTimeContractTest {

    @Test
    fun timerDurationUsesSingleClockDifferenceForSameDayMinuteBoundary() {
        val duration = calculateTimerDuration(
            now = LocalTime(hour = 10, minute = 50),
            target = LocalTime(hour = 11, minute = 10),
        )

        assertEquals(0, duration.hours)
        assertEquals(20, duration.minutes)
        assertTrue(duration.isPositive)
    }

    @Test
    fun timerDurationRollsPastTimesToNextDay() {
        val duration = calculateTimerDuration(
            now = LocalTime(hour = 23, minute = 50),
            target = LocalTime(hour = 0, minute = 10),
        )

        assertEquals(0, duration.hours)
        assertEquals(20, duration.minutes)
        assertTrue(duration.isPositive)
    }

    @Test
    fun timerDurationDisablesSameTimeTarget() {
        val duration = calculateTimerDuration(
            now = LocalTime(hour = 8, minute = 30),
            target = LocalTime(hour = 8, minute = 30),
        )

        assertEquals(0, duration.hours)
        assertEquals(0, duration.minutes)
        assertFalse(duration.isPositive)
    }

    @Test
    fun timerPickerDisplaysTwelveHourLabelsWithoutZeroOClock() {
        assertEquals(
            listOf("12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
            timerPickerHourLabels(),
        )
        assertEquals(0, timerPickerStartIndex(LocalTime(hour = 0, minute = 0)))
        assertEquals(0, timerPickerStartIndex(LocalTime(hour = 12, minute = 0)))
        assertEquals(1, timerPickerStartIndex(LocalTime(hour = 13, minute = 0)))
    }

    @Test
    fun timerPickerConvertsTwelveHourSelectionToTwentyFourHourTime() {
        assertEquals(
            LocalTime(hour = 0, minute = 5),
            timerPickerSelectedTime(isPm = false, hourLabel = "12", minute = 5),
        )
        assertEquals(
            LocalTime(hour = 12, minute = 5),
            timerPickerSelectedTime(isPm = true, hourLabel = "12", minute = 5),
        )
        assertEquals(
            LocalTime(hour = 1, minute = 5),
            timerPickerSelectedTime(isPm = false, hourLabel = "1", minute = 5),
        )
        assertEquals(
            LocalTime(hour = 13, minute = 5),
            timerPickerSelectedTime(isPm = true, hourLabel = "1", minute = 5),
        )
    }
}
