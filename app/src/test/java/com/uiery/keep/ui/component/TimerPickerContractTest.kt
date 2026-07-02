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

    @Test
    fun timerPickerSelectionFollowsExternallyProvidedTime() {
        assertEquals(
            TimerPickerSelection(isPm = false, hourLabel = "12", minuteLabel = "0"),
            timerPickerSelection(LocalTime(hour = 0, minute = 0)),
        )
        assertEquals(
            TimerPickerSelection(isPm = true, hourLabel = "3", minuteLabel = "45"),
            timerPickerSelection(LocalTime(hour = 15, minute = 45)),
        )
    }

    @Test
    fun timerPickerSuppressesCallbackWhenPickerSelectionMatchesExternalTime() {
        assertEquals(
            null,
            timerPickerSelectionOrNull(
                hasPeriodSelection = false,
                isPm = false,
                hourLabel = "9",
                minute = 30,
            ),
        )
        assertEquals(
            null,
            timerPickerChangedTimeOrNull(
                externalTime = LocalTime(hour = 9, minute = 30),
                pickerSelection = TimerPickerSelection(isPm = false, hourLabel = "9", minuteLabel = "30"),
            ),
        )
        assertEquals(
            LocalTime(hour = 10, minute = 30),
            timerPickerChangedTimeOrNull(
                externalTime = LocalTime(hour = 9, minute = 30),
                pickerSelection = TimerPickerSelection(isPm = false, hourLabel = "10", minuteLabel = "30"),
            ),
        )
    }
}
