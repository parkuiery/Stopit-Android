package com.uiery.keep.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.uiery.kds.theme.KeepTheme
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimerPickerIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun externallyProvidedTimeDoesNotEmitRedundantChangeCallbacks() {
        var timerTime by mutableStateOf(LocalTime(hour = 9, minute = 30))
        val emittedTimes = mutableListOf<LocalTime>()

        composeRule.setContent {
            KeepTheme {
                TimerPicker(
                    time = timerTime,
                    onChangeTimerTime = { emittedTimes += it },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(emptyList<LocalTime>(), emittedTimes)
            timerTime = LocalTime(hour = 15, minute = 45)
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(emptyList<LocalTime>(), emittedTimes)
        }
    }
}
