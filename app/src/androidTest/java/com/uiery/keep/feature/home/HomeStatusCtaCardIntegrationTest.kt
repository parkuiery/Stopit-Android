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
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
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
                        hasGoalLockCard = true,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
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
                        hasGoalLockCard = false,
                    ),
                    onPrimaryClick = {},
                    onChangeAppsClick = {},
                    onTimerClick = {},
                    onLockHistoryClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("home_status_cta_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_status_keep_active_title, 4)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_primary_status_keep_active)).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_change_apps)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_secondary_lock_history)).assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.home_secondary_timer)).assertCountEquals(0)
    }
}
