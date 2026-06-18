package com.uiery.keep.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.junit.Rule
import org.junit.Test

class HomeGoalLockProgressCardAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun activeScheduledGoalLockCardExposesTalkBackSummaryForWaitingWindow() {
        val expectedDescription = listOf(
            context.getString(R.string.home_goal_lock_card_title_active),
            "시험 준비",
            context.getString(
                R.string.home_goal_lock_card_summary_active_waiting_window,
                12,
                context.getString(R.string.home_goal_lock_card_lock_mode_scheduled),
                3,
            ),
        ).joinToString(", ")

        composeRule.setContent {
            KeepTheme {
                GoalLockProgressCard(
                    cardState = HomeGoalLockCardState(
                        goalLockId = 417L,
                        goalName = "시험 준비",
                        status = HomeGoalLockStatus.Active,
                        daysRemaining = 12,
                        lockMode = HomeGoalLockCardLockMode.Scheduled,
                        selectedAppCount = 3,
                        isCurrentlyProtecting = false,
                    ),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
    }

    @Test
    fun completedGoalLockCardExposesTerminalTalkBackSummary() {
        val expectedDescription = listOf(
            context.getString(R.string.home_goal_lock_card_title_completed),
            "SNS 줄이기",
            context.getString(
                R.string.home_goal_lock_card_summary_completed,
                0,
                context.getString(R.string.home_goal_lock_card_lock_mode_all_day),
                2,
            ),
        ).joinToString(", ")

        composeRule.setContent {
            KeepTheme {
                GoalLockProgressCard(
                    cardState = HomeGoalLockCardState(
                        goalLockId = 418L,
                        goalName = "SNS 줄이기",
                        status = HomeGoalLockStatus.Completed,
                        daysRemaining = 0,
                        lockMode = HomeGoalLockCardLockMode.AllDay,
                        selectedAppCount = 2,
                    ),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
    }
}
