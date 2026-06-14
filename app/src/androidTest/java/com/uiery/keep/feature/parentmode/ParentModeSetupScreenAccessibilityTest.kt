package com.uiery.keep.feature.parentmode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import org.junit.Rule
import org.junit.Test

class ParentModeSetupScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun setupFormExposesDurationAppsAndPinAsTalkBackSummary() {
        val state = ParentModeSetupUiState(
            durationMinutes = 45,
            customDurationInput = "45",
            allowedApps = setOf("com.example.video", "com.example.kids"),
            guardianPin = "1234",
            guardianPinConfirmation = "1234",
        )

        composeRule.setContent {
            KeepTheme {
                ParentModeSetupForm(
                    state = state,
                    pinMismatch = false,
                    onDurationSelected = {},
                    onCustomDurationChanged = {},
                    onReloadCurrentSelection = {},
                    onAdjustApps = {},
                    onGuardianPinChanged = {},
                    onGuardianPinConfirmationChanged = {},
                    onStart = {},
                    onNavigateBack = {},
                )
            }
        }

        val expectedSummary = context.getString(
            R.string.parent_mode_setup_accessibility_summary,
            45,
            2,
        )
        composeRule.onNode(hasContentDescription(expectedSummary)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_start))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun activeControlsExposeActiveSessionAsTalkBackSummaryAndEnabledActions() {
        val session = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 60,
            allowedApps = setOf("com.example.video"),
            state = ParentModeSessionState.Active,
        )

        composeRule.setContent {
            KeepTheme {
                ParentModeActiveControls(
                    session = session,
                    onRefresh = {},
                    onExtend = {},
                    onEnd = {},
                    onNavigateBack = {},
                )
            }
        }

        val expectedSummary = context.getString(
            R.string.parent_mode_active_accessibility_summary,
            context.getString(R.string.parent_mode_active_title),
            60,
            1,
        )
        composeRule.onNodeWithContentDescription(expectedSummary).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_extend_ten_minutes))
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_end_now))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun activeControlsKeepExpiredSessionActionsDisabled() {
        val session = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 30,
            allowedApps = setOf("com.example.video", "com.example.kids"),
            state = ParentModeSessionState.Expired,
        )

        composeRule.setContent {
            KeepTheme {
                ParentModeActiveControls(
                    session = session,
                    onRefresh = {},
                    onExtend = {},
                    onEnd = {},
                    onNavigateBack = {},
                )
            }
        }

        val expectedSummary = context.getString(
            R.string.parent_mode_active_accessibility_summary,
            context.getString(R.string.parent_mode_expired_title),
            30,
            2,
        )
        composeRule.onNodeWithContentDescription(expectedSummary).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_extend_ten_minutes))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_end_now))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
