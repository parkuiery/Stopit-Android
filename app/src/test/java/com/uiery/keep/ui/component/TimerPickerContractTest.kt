package com.uiery.keep.ui.component

import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerPickerContractTest {

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
