package com.uiery.keep.feature.routine.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    onBlockedRoutineAction = {},
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

    @Test
    fun runningRoutineCardClickSurfacesBlockedActionFeedbackInsteadOfOpeningDetail() {
        val today = LocalDateTime.now().dayOfWeek
        var blockedActionCount = 0
        var detailClickCount = 0

        composeRule.setContent {
            KeepTheme {
                RoutineListContent(
                    routines = listOf(
                        testRoutine(
                            id = 9L,
                            name = "Running focus",
                            startTime = LocalTime(hour = 0, minute = 0),
                            endTime = LocalTime(hour = 23, minute = 59),
                            repeatDays = listOf(today),
                            isEnabled = true,
                        ),
                    ),
                    onEnabledChange = { _, _ -> },
                    onDetailClick = { detailClickCount += 1 },
                    onShareClick = {},
                    onBlockedRoutineAction = { blockedActionCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Running focus").performClick()

        composeRule.runOnIdle {
            check(blockedActionCount == 1) {
                "Expected blocked routine tap to surface feedback once, got $blockedActionCount"
            }
            check(detailClickCount == 0) {
                "Blocked routine tap must not open detail, got $detailClickCount detail clicks"
            }
        }
    }

    @Test
    fun runningRoutineSwitchTapSurfacesBlockedActionFeedbackWithoutChangingEnabledState() {
        val today = LocalDateTime.now().dayOfWeek
        var blockedActionCount = 0
        var enabledChangeCount = 0

        composeRule.setContent {
            KeepTheme {
                RoutineListContent(
                    routines = listOf(
                        testRoutine(
                            id = 609L,
                            name = "Running focus",
                            startTime = LocalTime(hour = 0, minute = 0),
                            endTime = LocalTime(hour = 23, minute = 59),
                            repeatDays = listOf(today),
                            isEnabled = true,
                        ),
                    ),
                    onEnabledChange = { _, _ -> enabledChangeCount += 1 },
                    onDetailClick = {},
                    onShareClick = {},
                    onBlockedRoutineAction = { blockedActionCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("routine-enabled-switch-609").performClick()

        composeRule.runOnIdle {
            check(blockedActionCount == 1) {
                "Expected blocked routine switch tap to surface feedback once, got $blockedActionCount"
            }
            check(enabledChangeCount == 0) {
                "Blocked routine switch tap must not toggle the routine, got $enabledChangeCount changes"
            }
        }
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
