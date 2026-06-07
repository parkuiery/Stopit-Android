package com.uiery.keep.feature.goallock

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class GoalLockDetailContentIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeGoalLockShowsAppUpdateActionAndConfirmation() {
        val updateRequests = mutableListOf<Set<String>>()
        var confirmCount = 0
        var cancelCount = 0

        composeRule.setContent {
            KeepTheme {
                GoalLockDetailContent(
                    state = GoalLockDetailUiState(
                        goalLock = activeGoalLock(),
                        pendingSelectedApps = setOf("com.video.app", "com.social.app"),
                        showUpdateAppsConfirmation = true,
                    ),
                    onRequestEnd = {},
                    onCancelEnd = {},
                    onConfirmEnd = {},
                    onRequestUpdateApps = { selectedApps -> updateRequests += selectedApps },
                    onCancelUpdateApps = { cancelCount += 1 },
                    onConfirmUpdateApps = { confirmCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_update_apps_cta)).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.goal_lock_detail_update_apps_confirmation, 2),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_update_apps_save)).performClick()

        assertEquals(1, confirmCount)
        assertEquals(0, cancelCount)
        assertEquals(emptyList<Set<String>>(), updateRequests)
    }
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
