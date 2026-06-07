package com.uiery.keep.feature.goallock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
}
