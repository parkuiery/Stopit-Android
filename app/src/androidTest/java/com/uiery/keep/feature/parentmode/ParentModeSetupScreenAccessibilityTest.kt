package com.uiery.keep.feature.parentmode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ParentModeSetupScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun activeSession(
        durationMinutes: Int = 60,
        allowedApps: Set<String> = setOf("com.example.video"),
        state: ParentModeSessionState = ParentModeSessionState.Active,
    ) = ParentModeSession(
        startedAtMillis = 1_000L,
        expiresAtMillis = 61_000L,
        durationMinutes = durationMinutes,
        allowedApps = allowedApps,
        state = state,
    )

    @Test
    fun setupFormExposesDurationAndAppsAsTalkBackSummary() {
        val state = ParentModeSetupUiState(
            durationMinutes = 45,
            allowedApps = setOf("com.example.video", "com.example.kids"),
        )

        composeRule.setContent {
            KeepTheme {
                ParentModeSetupForm(
                    state = state,
                    onDurationSelected = {},
                    onDurationDialled = { _, _ -> },
                    onAdjustApps = {},
                )
            }
        }

        val expectedSummary = context.getString(
            R.string.parent_mode_setup_accessibility_summary,
            45,
            2,
        )
        // 시작 버튼은 화면의 하단 액션 바가 소유한다. 폼은 약속의 내용만 담는다.
        composeRule.onNode(hasContentDescription(expectedSummary)).assertIsDisplayed()
    }

    /**
     * The duration used to appear as a header label, a selected chip and a separate number field at
     * the same time. The wheel is the one place it is dialled now, and the PIN has left the form.
     */
    @Test
    fun setupFormDialsTheDurationOnAWheelAndAsksForNoPin() {
        composeRule.setContent {
            KeepTheme {
                ParentModeSetupForm(
                    state = ParentModeSetupUiState(durationMinutes = 45),
                    onDurationSelected = {},
                    onDurationDialled = { _, _ -> },
                    onAdjustApps = {},
                )
            }
        }

        composeRule.onNodeWithTag("parent_mode_duration_picker").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_pin_label))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_pin_confirm_label))
            .assertDoesNotExist()
    }

    /**
     * 목록에 상한이 없으면 앱을 많이 고를수록 아래 허용 시간 카드가 접히는 선 밖으로 밀려난다.
     * 앞의 3개만 남기고 나머지는 개수로 접어 카드 높이를 고정한다.
     */
    @Test
    fun setupFormCapsTheAllowedAppListAndSaysHowManyItFolded() {
        composeRule.setContent {
            KeepTheme {
                ParentModeSetupForm(
                    state = ParentModeSetupUiState(
                        allowedApps = setOf(
                            "com.a.app",
                            "com.b.app",
                            "com.c.app",
                            "com.d.app",
                            "com.e.app",
                        ),
                    ),
                    onDurationSelected = {},
                    onDurationDialled = { _, _ -> },
                    onAdjustApps = {},
                )
            }
        }

        composeRule.onNodeWithTag("parent_mode_setup_allowed_apps_overflow")
            .assertIsDisplayed()
        composeRule.onNodeWithText("com.d.app").assertDoesNotExist()
        composeRule.onNodeWithText("com.e.app").assertDoesNotExist()
    }

    @Test
    fun setupFormDoesNotFoldAnAllowedAppListThatAlreadyFits() {
        composeRule.setContent {
            KeepTheme {
                ParentModeSetupForm(
                    state = ParentModeSetupUiState(allowedApps = setOf("com.a.app", "com.b.app")),
                    onDurationSelected = {},
                    onDurationDialled = { _, _ -> },
                    onAdjustApps = {},
                )
            }
        }

        composeRule.onNodeWithTag("parent_mode_setup_allowed_apps_overflow").assertDoesNotExist()
    }

    @Test
    fun activeControlsExposeActiveSessionAsTalkBackSummaryAndLiveGuardianActions() {
        var extendClicks = 0
        var endClicks = 0

        composeRule.setContent {
            KeepTheme {
                ParentModeActiveControls(
                    session = activeSession(),
                    onRefresh = {},
                    onExtend = { extendClicks++ },
                    onEnd = { endClicks++ },
                    onPrepareAnother = {},
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

        // 진행 중 화면에는 PIN 칸이 없다. 두 버튼이 각각 보호자 PIN 시트를 연다.
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_extend_ten_minutes))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_end_now))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, extendClicks)
        assertEquals(1, endClicks)
    }

    /**
     * A finished session has nothing left to extend or end, so the guardian card is gone rather than
     * sitting there with two dead buttons. The unlock is the only thing left to do.
     */
    @Test
    fun activeControlsDropTheGuardianCardOnceTheSessionIsFinished() {
        composeRule.setContent {
            KeepTheme {
                ParentModeActiveControls(
                    session = activeSession(
                        durationMinutes = 30,
                        allowedApps = setOf("com.example.video", "com.example.kids"),
                        state = ParentModeSessionState.Expired,
                    ),
                    onRefresh = {},
                    onExtend = {},
                    onEnd = {},
                    onPrepareAnother = {},
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
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_active_end_now))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_expired_end_and_unlock))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun guardianPinSheetStatesTheScopeAndStaysLockedUntilThePinsMatch() {
        composeRule.setContent {
            KeepTheme {
                ParentModeGuardianPinSheet(
                    state = ParentModeSetupUiState(
                        durationMinutes = 45,
                        allowedApps = setOf("com.example.video", "com.example.kids"),
                        guardianPin = "1234",
                        guardianPinConfirmation = "9999",
                    ),
                    action = ParentModeGuardianAction.Start,
                    pinMismatch = true,
                    onGuardianPinChanged = {},
                    onGuardianPinConfirmationChanged = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithTag("parent_mode_guardian_pin_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("parent_mode_guardian_sheet_summary").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_pin_mismatch))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_start))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun guardianPinSheetUnlocksItsActionOnceThePinsMatch() {
        var confirmed = 0

        composeRule.setContent {
            KeepTheme {
                ParentModeGuardianPinSheet(
                    state = ParentModeSetupUiState(
                        durationMinutes = 45,
                        allowedApps = setOf("com.example.video"),
                        guardianPin = "1234",
                        guardianPinConfirmation = "1234",
                    ),
                    action = ParentModeGuardianAction.Start,
                    pinMismatch = false,
                    onGuardianPinChanged = {},
                    onGuardianPinConfirmationChanged = {},
                    onConfirm = { confirmed++ },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.parent_mode_setup_start))
            .assertIsEnabled()
            .performClick()

        assertEquals(1, confirmed)
    }
}
