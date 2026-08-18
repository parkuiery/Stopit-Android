package com.uiery.keep.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeStatusCtaCardIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun noSelectedAppsShowsAppSelectionAsOnlyPrimaryAction() {
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = false,
                        selectedAppCount = 0,
                        showFirstLockActivationCta = false,
                        showRoutineCreationCta = false,
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_status_cta_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_status_no_selected_apps_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_primary_cta_select_apps)).assertIsDisplayed().assertIsEnabled()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_timer)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_lock_history)).assertCountEquals(0)
    }

    @Test
    fun firstLockReadyKeepsImmediateStartPrimaryAndTimerSecondary() {
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = false,
                        selectedAppCount = 3,
                        showFirstLockActivationCta = true,
                        showRoutineCreationCta = false,
                        hasGoalLockCard = true,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_status_cta_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_status_first_lock_ready_title, 3)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_primary_cta_start_now)).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_change_apps)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_timer)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_lock_history)).assertCountEquals(0)
    }

    @Test
    fun keepActiveShowsProtectionStatusWithoutClickablePrimaryCta() {
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = true,
                        selectedAppCount = 4,
                        showFirstLockActivationCta = false,
                        showRoutineCreationCta = false,
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_status_cta_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_status_keep_active_title, 4)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_primary_status_keep_active)).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_change_apps)).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_lock_history)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_timer)).assertCountEquals(0)
    }

    @Test
    fun timedLockActiveShowsTimerStatusWithChangeTimeSecondaryAction() {
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = false,
                        selectedAppCount = 2,
                        showFirstLockActivationCta = false,
                        showRoutineCreationCta = true,
                        hasGoalLockCard = false,
                        hasActiveTimedLock = true,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_status_cta_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_status_timed_lock_active_title, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_primary_status_timed_lock_active)).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_change_apps)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_timer)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_lock_history)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_create_routine)).assertCountEquals(0)
    }

    /**
     * 노출 보고는 루틴 생성 액션이 실제로 컴포즈될 때만 일어난다 (#1166).
     * 상태만 true 라도 이 분기가 그려지지 않으면 보고되지 않아야 한다.
     */
    @Test
    fun routineCreationSecondaryReportsShownOnlyWhenRendered() {
        var shownReports = 0
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = false,
                        selectedAppCount = 2,
                        showFirstLockActivationCta = false,
                        showRoutineCreationCta = true,
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                    onRoutineCreationShown = { shownReports++ },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.home_secondary_create_routine)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, shownReports) }
    }

    @Test
    fun hiddenRoutineCreationSecondaryDoesNotReportShown() {
        var shownReports = 0
        composeRule.setContent {
            KeepTheme {
                HomeStatusCtaCard(
                    model = buildHomeStatusCtaModel(
                        isKeep = false,
                        selectedAppCount = 2,
                        showFirstLockActivationCta = false,
                        showRoutineCreationCta = false,
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                    onRoutineCreationClick = {},
                    onRoutineCreationShown = { shownReports++ },
                )
            }
        }

        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_create_routine)).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(0, shownReports) }
    }
}
