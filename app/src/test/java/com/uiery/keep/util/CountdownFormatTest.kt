package com.uiery.keep.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class CountdownFormatTest {

    @Test
    fun formatMinuteSecondCountdownUsesAsciiDigitsUnderArabicLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale("ar", "EG"))
        try {
            assertEquals("1:05", formatMinuteSecondCountdown(65))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun formatMinuteSecondCountdownKeepsSecondsZeroPadded() {
        assertEquals("10:09", formatMinuteSecondCountdown(609))
    }

    @Test
    fun formatHourAwareCountdownUsesClockStyleAcrossHourBoundary() {
        assertEquals("01:01:05", formatHourAwareCountdown(3665))
        assertEquals("59:05", formatHourAwareCountdown(3545))
    }

    @Test
    fun formatHourAwareCountdownClampsExpiredValuesToZero() {
        assertEquals("00:00", formatHourAwareCountdown(-1))
        assertEquals("00:00", formatHourAwareCountdown(0))
        assertEquals("00:01", formatHourAwareCountdown(1))
    }

    @Test
    fun formatLockEndTimeUsesAsciiDigitsUnderArabicLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale("ar", "EG"))
        try {
            assertEquals(
                "08:05",
                formatLockEndTime(
                    lockTime = LocalDateTime.of(2026, 5, 1, 8, 5),
                    currentDate = LocalDate.of(2026, 5, 1),
                ),
            )
            assertEquals(
                "5/2 08:05",
                formatLockEndTime(
                    lockTime = LocalDateTime.of(2026, 5, 2, 8, 5),
                    currentDate = LocalDate.of(2026, 5, 1),
                ),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
