package com.uiery.keep.feature.review

import com.uiery.keep.data.review.ReviewEligibilityRepository
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton


// 2026-08 readback: 30일 동안 review_prompt_shown 이 3건이었고 skip 사유의 72.4%가
// BelowSessionThreshold 였다. 성공 세션은 타이머 완주 경로에서만 집계되는데 그 경로는 전체
// 종료 세션의 3.8%뿐이라(GA4 79/2,054), 3회 누적은 사실상 도달 불가능한 조건이었다.
// docs/RETENTION_DIAGNOSIS_2026_08.md 2.4 / docs/REVIEW_PROMPT_POST_RELEASE_FOLLOWTHROUGH.md 참조.
private const val SESSION_THRESHOLD = 1
private const val COOLDOWN_MILLIS = 90L * 24 * 60 * 60 * 1000
private const val EMERGENCY_UNLOCK_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val EMERGENCY_UNLOCK_MAX_COUNT = 2
private const val RECENT_SUCCESS_WINDOW_MILLIS = 24L * 60 * 60 * 1000
private const val SAME_SESSION_THRESHOLD_MILLIS = 1_500L
private val QUIET_HOURS_RANGE = 1..5
private const val DEV_FLAVOR = "dev"

@Singleton
class ReviewEligibilityEvaluator @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val reviewPromptStateStore: ReviewPromptStateStore,
    private val remoteConfig: ReviewRemoteConfig,
    private val accessibilityChecker: AccessibilityChecker,
    private val repository: ReviewEligibilityRepository,
    private val clock: Clock,
    private val buildConfig: ReviewBuildConfig,
) {

    suspend fun evaluate(
        nowMs: Long,
        durationMillis: Long,
        isRoutine: Boolean,
        includeCurrentSuccessfulSession: Boolean = false,
    ): ReviewEligibilityDecision {
        if (!remoteConfig.isEnabled()) return ineligible(SkipReason.KillSwitch)
        if (buildConfig.isDebug) return ineligible(SkipReason.Debug)
        if (buildConfig.flavor == DEV_FLAVOR) return ineligible(SkipReason.DevFlavor)
        if (!accessibilityChecker.isEnabled()) return ineligible(SkipReason.AccessibilityOff)
        if (isQuietHours()) return ineligible(SkipReason.QuietHours)

        val reviewPromptState = reviewPromptStateStore.readState()
        val sessionCount = blockingStateStore.readSuccessfulSessionCount()
        if (sessionCount < SESSION_THRESHOLD) return ineligible(SkipReason.BelowSessionThreshold)

        val lastPromptAt = reviewPromptState.lastPromptAtMs
        if (lastPromptAt != null && nowMs - lastPromptAt < COOLDOWN_MILLIS) {
            return ineligible(SkipReason.WithinCooldown)
        }

        val lastBackgroundedAt = reviewPromptState.lastBackgroundedAtMs
        if (lastBackgroundedAt == null) return ineligible(SkipReason.NoBackgroundingObserved)
        if (nowMs - lastBackgroundedAt <= SAME_SESSION_THRESHOLD_MILLIS) {
            return ineligible(SkipReason.WithinSameSession)
        }

        val emergencyCount = repository.countRecentEmergencyUnlocks(nowMs - EMERGENCY_UNLOCK_WINDOW_MILLIS)
        if (emergencyCount >= EMERGENCY_UNLOCK_MAX_COUNT) return ineligible(SkipReason.RecentEmergencyUnlock)

        val recentSuccess =
            repository.countRecentSuccessfulSessions(nowMs - RECENT_SUCCESS_WINDOW_MILLIS) +
                if (includeCurrentSuccessfulSession) 1 else 0
        if (recentSuccess < 1) return ineligible(SkipReason.NoRecentSuccess)

        return ReviewEligibilityDecision.Eligible
    }

    fun evaluateLive(): ReviewEligibilityDecision {
        if (!remoteConfig.isEnabled()) return ineligible(SkipReason.KillSwitch)
        if (!accessibilityChecker.isEnabled()) return ineligible(SkipReason.AccessibilityOff)
        if (isQuietHours()) return ineligible(SkipReason.QuietHours)
        return ReviewEligibilityDecision.Eligible
    }

    private fun isQuietHours(): Boolean = LocalTime.now(clock).hour in QUIET_HOURS_RANGE

    private fun ineligible(reason: SkipReason): ReviewEligibilityDecision =
        ReviewEligibilityDecision.Ineligible(reason)
}
