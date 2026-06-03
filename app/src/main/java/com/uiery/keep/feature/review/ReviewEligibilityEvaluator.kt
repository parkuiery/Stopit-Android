package com.uiery.keep.feature.review

import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton


private const val SESSION_THRESHOLD = 3
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
    private val emergencyUnlockDao: EmergencyUnlockDao,
    private val lockHistoryDao: LockHistoryDao,
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

        val emergencyCount = emergencyUnlockDao.countSince(nowMs - EMERGENCY_UNLOCK_WINDOW_MILLIS)
        if (emergencyCount >= EMERGENCY_UNLOCK_MAX_COUNT) return ineligible(SkipReason.RecentEmergencyUnlock)

        val recentSuccess =
            lockHistoryDao.countSuccessfulSessionsSince(nowMs - RECENT_SUCCESS_WINDOW_MILLIS) +
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
