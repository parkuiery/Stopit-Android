package com.uiery.keep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.service.EmergencyUnlockAvailabilityReason
import org.junit.Rule
import org.junit.Test

class BlockScreenContentIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun coachingCopyKeepsEmergencyUnlockSecondaryToPrimaryReturnAction() {
        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    uiState = BlockUiState(
                        dailyUnlockRemaining = 2,
                        emergencyUnlockDailyLimit = 3,
                        emergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.Available,
                    ),
                    showBannerAd = false,
                    onShowEmergencyUnlock = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("block_screen_copy_area").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.block_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.block_screen_message, "YouTube")).assertIsDisplayed()

        composeRule.onNodeWithTag("block_screen_emergency_unlock_action").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_with_count, 2, 3)).assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_emergency_unlock_helper").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_available_helper)).assertIsDisplayed()

        composeRule.onNodeWithTag("block_screen_close_cta").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.block_screen_close)).assertIsDisplayed()
    }

    @Test
    fun disabledEmergencyUnlockStillExplainsWhyTheSecondaryActionIsUnavailable() {
        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    uiState = BlockUiState(
                        emergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.DailyLimitExhausted,
                    ),
                    showBannerAd = false,
                    onShowEmergencyUnlock = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("block_screen_emergency_unlock_action").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_daily_limit_reached)).assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_emergency_unlock_helper").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_daily_limit_reached_helper)).assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_close_cta").assertIsDisplayed().assertIsEnabled()
    }
}
