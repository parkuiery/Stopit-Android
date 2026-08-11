package com.uiery.keep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.domain.repeatblock.RepeatBlockCategoryBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockCountBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockDayType
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.repeatblock.RepeatBlockSuggestionReason
import com.uiery.keep.domain.repeatblock.RepeatBlockTimeBucket
import com.uiery.keep.domain.repeatblock.RoutineCoverageState
import com.uiery.keep.lockscreen.LockScreenMode
import com.uiery.keep.service.EmergencyUnlockAvailabilityReason
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class BlockScreenContentIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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

    @Test
    fun activeRoutineBlockExplainsRoutineReasonWhileKeepingEmergencyUnlockSecondary() {
        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    blockMode = LockScreenMode.Routine,
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
        composeRule.onNodeWithText(context.getString(R.string.block_screen_routine_active_reason)).assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_emergency_unlock_action").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.emergency_unlock_with_count, 2, 3)).assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_close_cta").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun postBlockSuccessRepeatSuggestionSurfacesStableQaActions() {
        var applyClicks = 0
        var dismissClicks = 0
        val suggestion = RepeatBlockRoutineSuggestion(
            timeBucket = RepeatBlockTimeBucket.Night,
            dayType = RepeatBlockDayType.Weekday,
            categoryBucket = RepeatBlockCategoryBucket.Video,
            repeatCountBucket = RepeatBlockCountBucket.ThreeToFive,
            routineCoverageState = RoutineCoverageState.NotCovered,
            reason = RepeatBlockSuggestionReason.RapidRetry,
            prefillPackages = listOf("com.example.video", "com.example.shortvideo"),
            prefillStartTime = LocalTime(hour = 22, minute = 0),
            prefillEndTime = LocalTime(hour = 23, minute = 0),
        )

        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    uiState = BlockUiState(repeatBlockRoutineSuggestion = suggestion),
                    showBannerAd = false,
                    onShowEmergencyUnlock = {},
                    onOpenRoutineSuggestion = { applyClicks += 1 },
                    onDismissRoutineSuggestion = { dismissClicks += 1 },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("block_screen_repeat_block_suggestion_card").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.repeat_block_suggestion_post_block_success_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.repeat_block_suggestion_post_block_success_message,
                2,
                suggestion.prefillStartTime,
                suggestion.prefillEndTime,
            ),
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("block_screen_repeat_block_suggestion_apply_action")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("block_screen_repeat_block_suggestion_dismiss_action")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, applyClicks)
        assertEquals(1, dismissClicks)
    }

    @Test
    fun theWayOutSurvivesTheFullestBlockScreen() {
        // 첫 차단은 안내 문구·카운트다운·루틴 사유·반복 제안이 한꺼번에 붙는 회차다. 문구가
        // 넘칠 때 아래 행동 묶음까지 밀려나면, 시스템 뒤로가기가 막혀 있어 사용자는 이 화면에
        // 갇힌다. 넘치는 쪽은 문구여야 하고 나가는 길은 남아야 한다.
        val suggestion = RepeatBlockRoutineSuggestion(
            timeBucket = RepeatBlockTimeBucket.Night,
            dayType = RepeatBlockDayType.Weekday,
            categoryBucket = RepeatBlockCategoryBucket.Video,
            repeatCountBucket = RepeatBlockCountBucket.ThreeToFive,
            routineCoverageState = RoutineCoverageState.NotCovered,
            reason = RepeatBlockSuggestionReason.RapidRetry,
            prefillPackages = listOf("com.example.video", "com.example.shortvideo"),
            prefillStartTime = LocalTime(hour = 22, minute = 0),
            prefillEndTime = LocalTime(hour = 23, minute = 0),
        )

        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    blockMode = LockScreenMode.Routine,
                    uiState = BlockUiState(
                        dailyUnlockRemaining = 2,
                        emergencyUnlockDailyLimit = 3,
                        emergencyUnlockAvailabilityReason = EmergencyUnlockAvailabilityReason.Available,
                        showFirstCoreActionFeedback = true,
                        timedLockDeadline = java.time.LocalDateTime.now().plusHours(1),
                        repeatBlockRoutineSuggestion = suggestion,
                    ),
                    showBannerAd = false,
                    onShowEmergencyUnlock = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("block_screen_close_cta").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("block_screen_emergency_unlock_action").assertIsDisplayed()
        composeRule.onNodeWithTag("block_screen_repeat_block_suggestion_card").assertIsDisplayed()

        // 첫 차단 안내는 잘려서 사라지는 것이 아니라 스크롤로 닿을 수 있어야 한다.
        composeRule.onNodeWithTag("block_screen_copy_area")
            .performScrollToNode(
                hasText(context.getString(R.string.block_screen_first_core_action_feedback)),
            )
        composeRule.onNodeWithText(context.getString(R.string.block_screen_first_core_action_feedback))
            .assertIsDisplayed()
    }

    @Test
    fun repeatedSystemBackDoesNotDismissTheProtectionScreen() {
        var closeCount = 0
        composeRule.setContent {
            KeepTheme {
                BlockScreenContent(
                    appName = "YouTube",
                    uiState = BlockUiState(),
                    showBannerAd = false,
                    onShowEmergencyUnlock = {},
                    onClose = { closeCount += 1 },
                )
            }
        }

        repeat(3) {
            composeRule.runOnUiThread {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
            composeRule.waitForIdle()
        }

        assertEquals("System back must not trigger the allowed close path", 0, closeCount)
        assertNotEquals(
            "System back must be consumed so the blocking Activity stays visible",
            Lifecycle.State.DESTROYED,
            composeRule.activity.lifecycle.currentState,
        )
        composeRule.onNodeWithTag("block_screen_close_cta").assertIsDisplayed().assertIsEnabled()
    }
}
