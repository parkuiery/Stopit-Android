package com.uiery.keep.feature.routine.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineListContentIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun routineCardsRenderStatusRepeatAndNextRunLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = LocalDateTime.now()
        val today = now.dayOfWeek
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val todayLabel = today.getDisplayName(TextStyle.SHORT, locale)

        composeRule.setContent {
            KeepTheme {
                RoutineListContent(
                    routines = listOf(
                        testRoutine(
                            id = 1L,
                            name = "Morning focus",
                            startTime = LocalTime(hour = 23, minute = 59),
                            endTime = LocalTime(hour = 23, minute = 59),
                            repeatDays = listOf(today),
                            isEnabled = true,
                        ),
                        testRoutine(
                            id = 2L,
                            name = "Rest day",
                            startTime = LocalTime(hour = 7, minute = 0),
                            endTime = LocalTime(hour = 8, minute = 0),
                            repeatDays = listOf(today),
                            isEnabled = false,
                        ),
                        testRoutine(
                            id = 3L,
                            name = "Running focus",
                            startTime = LocalTime(hour = 0, minute = 0),
                            endTime = LocalTime(hour = 23, minute = 59),
                            repeatDays = listOf(today),
                            isEnabled = true,
                        ),
                    ),
                    onEnabledChange = { _, _ -> },
                    onDetailClick = {},
                    onShareClick = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.routine_enabled_tag)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.routine_disabled_tag)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.routine_running_tag)).assertIsDisplayed()
        composeRule.onAllNodesWithText(todayLabel, substring = true).assertCountEquals(3)
        composeRule.onAllNodesWithText(
            context.getString(R.string.routine_next_run_label, ""),
            substring = true,
        ).assertCountEquals(2)
        composeRule.onNodeWithText("Rest day").assertIsDisplayed()
    }

    private fun testRoutine(
        id: Long,
        name: String,
        startTime: LocalTime,
        endTime: LocalTime,
        repeatDays: List<DayOfWeek>,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toRepeatDaysBinary(),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}
