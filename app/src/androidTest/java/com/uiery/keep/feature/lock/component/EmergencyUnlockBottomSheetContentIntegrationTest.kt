package com.uiery.keep.feature.lock.component

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.service.EMERGENCY_UNLOCK_REASON_NOT_REQUIRED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyUnlockBottomSheetContentIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reasonEnabledSheetRequiresCustomReasonThenSubmitsSelectedReasonPayload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val unlockedRequests = mutableListOf<EmergencyUnlockBottomSheetRequest>()

        composeRule.setContent {
            KeepTheme {
                EmergencyUnlockBottomSheetContent(
                    blockedApps = setOf(packageName),
                    durationOptions = listOf(5, 10),
                    reasonStepEnabled = true,
                    onUnlock = { reason, customReason, apps, durationMinutes ->
                        unlockedRequests += EmergencyUnlockBottomSheetRequest(
                            reason = reason,
                            customReason = customReason,
                            apps = apps,
                            durationMinutes = durationMinutes,
                        )
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_reason_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_reason_helper)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_reason_required_helper)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).assertIsNotEnabled()

        composeRule.onNodeWithTag("emergency_unlock_reason_habit").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_reason_habit_reflection)).assertExists()

        composeRule.onNodeWithTag("emergency_unlock_reason_other").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_reason_other_required_helper)).assertExists()
        composeRule.onNodeWithTag("emergency_unlock_reason_other_input").performTextInput("은행 인증")
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_apps_helper)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_apps_required_helper)).assertExists()
        composeRule.onNodeWithTag("emergency_unlock_app_$packageName").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_duration_helper)).assertExists()
        composeRule.onNodeWithTag("emergency_unlock_duration_10").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_request)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_waiting)).assertExists()
        composeRule.mainClock.autoAdvance = false

        repeat(30) {
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }

        assertEquals(
            listOf(
                EmergencyUnlockBottomSheetRequest(
                    reason = "other",
                    customReason = "은행 인증",
                    apps = setOf(packageName),
                    durationMinutes = 10,
                )
            ),
            unlockedRequests,
        )
    }

    @Test
    fun reasonDisabledSheetCanSelectAppReachCountdownAndCancel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val unlockedRequests = mutableListOf<EmergencyUnlockBottomSheetRequest>()
        var dismissed = false

        composeRule.setContent {
            KeepTheme {
                EmergencyUnlockBottomSheetContent(
                    blockedApps = setOf(packageName),
                    durationOptions = listOf(5, 10),
                    reasonStepEnabled = false,
                    onUnlock = { reason, customReason, apps, durationMinutes ->
                        unlockedRequests += EmergencyUnlockBottomSheetRequest(
                            reason = reason,
                            customReason = customReason,
                            apps = apps,
                            durationMinutes = durationMinutes,
                        )
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_select_apps)).assertExists()
        composeRule.onNodeWithTag("emergency_unlock_app_$packageName").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).assertIsEnabled().performClick()

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_select_duration)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_request)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_waiting)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_cancel)).performClick()

        assertTrue(dismissed)
        assertEquals(emptyList<EmergencyUnlockBottomSheetRequest>(), unlockedRequests)
    }

    @Test
    fun countdownStepExposesTalkBackDescriptionWithRemainingSecondsAndCancelHint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName

        composeRule.setContent {
            KeepTheme {
                EmergencyUnlockBottomSheetContent(
                    blockedApps = setOf(packageName),
                    durationOptions = listOf(5, 10),
                    reasonStepEnabled = false,
                    onUnlock = { _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("emergency_unlock_app_$packageName").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_request)).performClick()

        composeRule.onNodeWithContentDescription(
            listOf(
                context.getString(R.string.emergency_unlock_waiting),
                context.getString(R.string.emergency_unlock_waiting_seconds, 30),
                context.getString(R.string.emergency_unlock_cancel),
            ).joinToString(". ")
        ).assertExists()
    }

    @Test
    fun reasonDisabledSheetSubmitsExistingPayloadAfterCountdown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        val unlockedRequests = mutableListOf<EmergencyUnlockBottomSheetRequest>()

        composeRule.setContent {
            KeepTheme {
                EmergencyUnlockBottomSheetContent(
                    blockedApps = setOf(packageName),
                    durationOptions = listOf(5, 10),
                    reasonStepEnabled = false,
                    onUnlock = { reason, customReason, apps, durationMinutes ->
                        unlockedRequests += EmergencyUnlockBottomSheetRequest(
                            reason = reason,
                            customReason = customReason,
                            apps = apps,
                            durationMinutes = durationMinutes,
                        )
                    },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("emergency_unlock_app_$packageName").performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_next)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_select_duration)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_request)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_waiting)).assertExists()
        composeRule.mainClock.autoAdvance = false

        repeat(30) {
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }

        assertEquals(
            listOf(
                EmergencyUnlockBottomSheetRequest(
                    reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
                    customReason = null,
                    apps = setOf(packageName),
                    durationMinutes = 5,
                )
            ),
            unlockedRequests,
        )
    }
}
