package com.uiery.keep.feature.goallock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GoalLockDetailContentIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeGoalLockShowsEndActionAndInvokesRequest() {
        var requestCount = 0

        composeRule.setContent {
            KeepTheme {
                GoalLockDetailContent(
                    state = activeGoalLockState(),
                    onRequestEnd = { requestCount += 1 },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_end_cta))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, requestCount)
    }

    @Test
    fun compactHeightDetailContentScrollsToEndAction() {
        lateinit var endText: String

        composeRule.setContent {
            endText = composeRule.activity.getString(R.string.goal_lock_detail_end_cta)
            KeepTheme {
                Box(modifier = Modifier.height(320.dp)) {
                    GoalLockDetailContent(
                        state = activeGoalLockState(),
                        onRequestEnd = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(endText).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailContentExposesTalkBackSummaryForGoalStatusModeAndSelectedApps() {
        val expectedDescription = listOf(
            "시험 준비",
            composeRule.activity.getString(
                R.string.goal_lock_detail_progress_active,
                24,
            ),
            composeRule.activity.getString(
                R.string.goal_lock_detail_period_value,
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 7, 3),
                30,
            ),
            composeRule.activity.getString(R.string.goal_lock_detail_lock_mode_all_day),
            composeRule.activity.getString(R.string.goal_lock_edit_apps_summary, 2),
            composeRule.activity.getString(R.string.goal_lock_detail_status_active),
        ).joinToString(", ")

        composeRule.setContent {
            KeepTheme {
                GoalLockDetailContent(
                    state = activeGoalLockState(),
                    onRequestEnd = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(expectedDescription).assertIsDisplayed()
    }
}

private fun activeGoalLockState(): GoalLockDetailUiState {
    val goalLock = activeGoalLock()
    return GoalLockDetailUiState(
        isLoading = false,
        goalLock = goalLock,
        presentation = goalLockDetailPresentation(
            goalLock = goalLock,
            today = LocalDate.of(2026, 6, 10),
        ),
    )
}

private fun activeGoalLock() =
    GoalLock(
        id = 42L,
        goalName = "시험 준비",
        startDate = LocalDate.of(2026, 6, 4),
        endDate = LocalDate.of(2026, 7, 3),
        lockMode = GoalLockMode.AllDay,
        selectedPackages = setOf("com.video.app", "com.social.app"),
        status = GoalLockStoredStatus.Active,
    )
