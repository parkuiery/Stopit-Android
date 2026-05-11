package com.uiery.keep.feature.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.datastore.PreferencesKey
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

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
    @KeepDataSource private val dataStore: DataStore<Preferences>,
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
    ): ReviewEligibilityDecision {
        if (!remoteConfig.isEnabled()) return ineligible(SkipReason.KillSwitch)
        if (buildConfig.isDebug) return ineligible(SkipReason.Debug)
        if (buildConfig.flavor == DEV_FLAVOR) return ineligible(SkipReason.DevFlavor)
        if (!accessibilityChecker.isEnabled()) return ineligible(SkipReason.AccessibilityOff)
        if (isQuietHours()) return ineligible(SkipReason.QuietHours)

        val prefs = dataStore.data.firstOrNull()
        val sessionCount = prefs?.get(PreferencesKey.SUCCESSFUL_SESSION_COUNT) ?: 0
        if (sessionCount < SESSION_THRESHOLD) return ineligible(SkipReason.BelowSessionThreshold)

        val lastPromptAt = prefs?.get(PreferencesKey.LAST_REVIEW_PROMPT_AT_MS)
        if (lastPromptAt != null && nowMs - lastPromptAt < COOLDOWN_MILLIS) {
            return ineligible(SkipReason.WithinCooldown)
        }

        val lastBackgroundedAt = prefs?.get(PreferencesKey.LAST_BACKGROUNDED_AT_MS)
        if (lastBackgroundedAt == null) return ineligible(SkipReason.NoBackgroundingObserved)
        if (nowMs - lastBackgroundedAt <= SAME_SESSION_THRESHOLD_MILLIS) {
            return ineligible(SkipReason.WithinSameSession)
        }

        val emergencyCount = emergencyUnlockDao.countSince(nowMs - EMERGENCY_UNLOCK_WINDOW_MILLIS)
        if (emergencyCount >= EMERGENCY_UNLOCK_MAX_COUNT) return ineligible(SkipReason.RecentEmergencyUnlock)

        val recentSuccess = lockHistoryDao.countSuccessfulSessionsSince(nowMs - RECENT_SUCCESS_WINDOW_MILLIS)
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
