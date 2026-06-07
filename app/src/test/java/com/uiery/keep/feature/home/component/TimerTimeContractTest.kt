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

}
