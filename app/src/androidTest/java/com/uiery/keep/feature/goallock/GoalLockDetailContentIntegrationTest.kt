package com.uiery.keep.feature.goallock

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
                    onGoalNameChange = {},
                    onCancelUpdateGoalName = {},
                    onConfirmUpdateGoalName = {},
                    onRequestUpdateApps = { selectedApps -> updateRequests += selectedApps },
                    onCancelUpdateApps = { cancelCount += 1 },
                    onConfirmUpdateApps = { confirmCount += 1 },
                    onDurationDaysChange = {},
                    onCancelUpdateDuration = {},
                    onConfirmUpdateDuration = {},
                    onLockModeChange = {},
                    onCancelUpdateLockMode = {},
                    onConfirmUpdateLockMode = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_goal_name_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_update_apps_cta)).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.goal_lock_detail_update_apps_confirmation, 2),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.goal_lock_detail_update_apps_save)).performClick()

        assertEquals(1, confirmCount)
        assertEquals(0, cancelCount)
        assertEquals(emptyList<Set<String>>(), updateRequests)
    }

    @Test
    fun compactHeightDetailContentScrollsToEndAction() {
        lateinit var endText: String

        composeRule.setContent {
            endText = composeRule.activity.getString(R.string.goal_lock_detail_end_cta)
            KeepTheme {
                Box(modifier = Modifier.height(320.dp)) {
                    GoalLockDetailContent(
                        state = GoalLockDetailUiState(goalLock = activeGoalLock()),
                        onRequestEnd = {},
                        onCancelEnd = {},
                        onConfirmEnd = {},
                        onGoalNameChange = {},
                        onCancelUpdateGoalName = {},
                        onConfirmUpdateGoalName = {},
                        onRequestUpdateApps = {},
                        onCancelUpdateApps = {},
                        onConfirmUpdateApps = {},
                        onDurationDaysChange = {},
                        onCancelUpdateDuration = {},
                        onConfirmUpdateDuration = {},
                        onLockModeChange = {},
                        onCancelUpdateLockMode = {},
                        onConfirmUpdateLockMode = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(endText).performScrollTo().assertIsDisplayed()
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
