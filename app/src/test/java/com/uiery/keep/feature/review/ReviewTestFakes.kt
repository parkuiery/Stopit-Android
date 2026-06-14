package com.uiery.keep.feature.review

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.database.entity.LockHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }

    fun snapshot(): Preferences = state.value

    companion object {
        fun withPrefs(block: androidx.datastore.preferences.core.MutablePreferences.() -> Unit): FakeDataStore {
            val mp = mutablePreferencesOf()
            mp.block()
            return FakeDataStore(mp)
        }
    }
}

class FakeReviewRemoteConfig(var enabled: Boolean = true) : ReviewRemoteConfig {
    override fun isEnabled(): Boolean = enabled
}

class FakeAccessibilityChecker(var enabled: Boolean = true) : AccessibilityChecker {
    override fun isEnabled(): Boolean = enabled
}

class FakeReviewLauncher(
    var nextResult: ReviewLaunchResult = ReviewLaunchResult.Success,
) : ReviewLauncher {
    var launchCount: Int = 0
        private set

    override suspend fun launch(activity: Activity): ReviewLaunchResult {
        launchCount += 1
        return nextResult
    }
}

class RecordingKeepAnalytics : KeepAnalytics {
    val events = mutableListOf<AnalyticsEventRecord>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun reviewPromptEligible() {
        events += AnalyticsEventRecord.Eligible
    }

    override fun reviewPromptShown() {
        events += AnalyticsEventRecord.Shown
    }

    override fun reviewPromptSkipped(reason: String) {
        events += AnalyticsEventRecord.Skipped(reason)
    }

    override fun reviewPromptFailed(error: String) {
        events += AnalyticsEventRecord.Failed(error)
    }
}

sealed interface AnalyticsEventRecord {
    data object Eligible : AnalyticsEventRecord
    data object Shown : AnalyticsEventRecord
    data class Skipped(val reason: String) : AnalyticsEventRecord
    data class Failed(val error: String) : AnalyticsEventRecord
}

class FakeEmergencyUnlockDao(var countSinceResult: Int = 0) : EmergencyUnlockDao {
    override suspend fun insert(entity: EmergencyUnlockEntity): Long = 1L
    override suspend fun deleteById(id: Long) = Unit
    override fun fetchByDateRange(start: Long, end: Long): Flow<List<EmergencyUnlockEntity>> = emptyFlow()
    override suspend fun countToday(todayStart: Long): Int = 0
    override suspend fun countSince(timestampMillis: Long): Int = countSinceResult
}

class FakeLockHistoryDao(var recentSuccessCount: Int = 1, var totalCount: Int = 0) : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit
    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = emptyFlow()
    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()
    override suspend fun countSuccessfulSessions(): Int = totalCount
    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int = recentSuccessCount
}

fun fakeReviewEligibilityRepository(
    emergencyCount: Int = 0,
    recentSuccessCount: Int = 1,
): ReviewEligibilityRepository = ReviewEligibilityRepository(
    emergencyUnlockDao = FakeEmergencyUnlockDao(emergencyCount),
    lockHistoryDao = FakeLockHistoryDao(recentSuccessCount = recentSuccessCount),
)
