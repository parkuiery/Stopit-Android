package com.uiery.keep.feature.goallock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.junit.Rule
import org.junit.Test

class GoalLockCreationContentIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactHeightCreationContentScrollsToSubmitAction() {
        lateinit var submitText: String

        composeRule.setContent {
            submitText = LocalContext.current.getString(R.string.goal_lock_creation_submit)
            KeepTheme {
                Box(modifier = Modifier.height(320.dp)) {
                    GoalLockCreationContent(
                        state = GoalLockCreationUiState(
                            goalName = "시험 준비",
                            selectedApps = setOf("com.video.app"),
                            isCreateEnabled = true,
                        ),
                        onGoalNameChange = {},
                        onPresetExam = {},
                        onPresetSns = {},
                        onSelectSevenDays = {},
                        onSelectFourteenDays = {},
                        onSelectThirtyDays = {},
                        onCustomDaysChange = {},
                        onEndDateChange = {},
                        onSetAllDay = {},
                        onSetWeekdayEvening = {},
                        onReloadCurrentSelection = {},
                        onSelectApps = {},
                        onRemoveSelectedApp = {},
                        onCreate = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(submitText).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun creationContentExposesTalkBackSummaryForGoalDurationModeAndSelectedApps() {
        val expectedDescription = listOf(
            "시험 준비",
            composeRule.activity.getString(
                R.string.goal_lock_creation_duration_range,
                "2026-06-04",
                "2026-07-03",
            ),
            composeRule.activity.getString(R.string.goal_lock_creation_current_mode_all_day),
            composeRule.activity.getString(R.string.goal_lock_creation_selected_apps_helper, 1),
        ).joinToString(", ")

        composeRule.setContent {
            KeepTheme {
                GoalLockCreationContent(
                    state = GoalLockCreationUiState(
                        goalName = "시험 준비",
                        startDate = java.time.LocalDate.of(2026, 6, 4),
                        endDate = java.time.LocalDate.of(2026, 7, 3),
                        selectedApps = setOf("com.video.app"),
                        isCreateEnabled = true,
                    ),
                    onGoalNameChange = {},
                    onPresetExam = {},
                    onPresetSns = {},
                    onSelectSevenDays = {},
                    onSelectFourteenDays = {},
                    onSelectThirtyDays = {},
                    onCustomDaysChange = {},
                    onEndDateChange = {},
                    onSetAllDay = {},
                    onSetWeekdayEvening = {},
                    onReloadCurrentSelection = {},
                    onSelectApps = {},
                    onRemoveSelectedApp = {},
                    onCreate = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
    }
}
