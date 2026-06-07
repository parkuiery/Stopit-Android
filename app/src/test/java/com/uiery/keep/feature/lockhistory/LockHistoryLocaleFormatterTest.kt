package com.uiery.keep.feature.lockhistory

import com.uiery.keep.util.formatMonthDayLabel
import com.uiery.keep.util.formatWeekdayShort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class LockHistoryLocaleFormatterTest {
    @Test
    fun weekdayUsesProvidedAppLocaleInsteadOfSystemDefaultLocale() {
        val originalDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            val weekday = formatWeekdayShort(
                dayOfWeek = DayOfWeek.MONDAY,
                locale = Locale.KOREAN,
            )

            assertEquals("월", weekday)
            assertNotEquals("Mon", weekday)
        } finally {
            Locale.setDefault(originalDefault)
        }
    }

    @Test
    fun monthDayLabelUsesProvidedAppLocaleInsteadOfSystemDefaultLocale() {
        val originalDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            val label = formatMonthDayLabel(
                date = LocalDate.of(2026, 6, 8),
                locale = Locale.KOREAN,
            )

            assertEquals("6월 8", label)
            assertNotEquals("June 8", label)
        } finally {
            Locale.setDefault(originalDefault)
        }
    }
}
