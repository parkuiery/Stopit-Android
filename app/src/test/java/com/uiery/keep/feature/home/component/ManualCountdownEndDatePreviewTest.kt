package com.uiery.keep.feature.home.component

import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCountdownEndDatePreviewTest {

    @Test
    fun countdownPreviewDoesNotAddExtraDayWhenRenderTimePassesSelectedBlockTime() {
        val preview = calculateCountdownEndDateTimePreview(
            today = LocalDate.of(2026, 6, 15),
            now = LocalTime(hour = 10, minute = 31),
            countdownDays = 1,
            countdownTime = LocalTime(hour = 0, minute = 0),
        )

        assertEquals(LocalDateTime.of(2026, 6, 16, 10, 31), preview)
    }

    @Test
    fun countdownPreviewAddsCalendarDayOnlyWhenSelectedDurationCrossesMidnight() {
        val preview = calculateCountdownEndDateTimePreview(
            today = LocalDate.of(2026, 6, 15),
            now = LocalTime(hour = 23, minute = 30),
            countdownDays = 1,
            countdownTime = LocalTime(hour = 1, minute = 0),
        )

        assertEquals(LocalDateTime.of(2026, 6, 17, 0, 30), preview)
    }

    @Test
    fun countdownPreviewKeepsZeroDayDurationOnTodayWhenItDoesNotCrossMidnight() {
        val preview = calculateCountdownEndDateTimePreview(
            today = LocalDate.of(2026, 6, 15),
            now = LocalTime(hour = 9, minute = 0),
            countdownDays = 0,
            countdownTime = LocalTime(hour = 2, minute = 15),
        )

        assertEquals(LocalDateTime.of(2026, 6, 15, 11, 15), preview)
    }
}
