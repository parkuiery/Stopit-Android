package com.uiery.keep.feature.lockhistory.blockedapps

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedAppsViewModelAnalyticsTest {
    @Test
    fun initLogsBlockedAppsScreenView() = runBlocking {
        val analytics = RecordingBlockedAppsAnalytics()

        BlockedAppsViewModel(
            lockHistoryRepository = LockHistoryRepository(EmptyLockHistoryDao),
            analytics = analytics,
        )

        waitUntil { analytics.screenViews.isNotEmpty() }

        assertEquals(listOf(KeepAnalyticsScreen.BLOCKED_APPS), analytics.screenViews)
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(10)
        }
    }
}

private object EmptyLockHistoryDao : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit
    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = flowOf(emptyList())
    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(emptyList())
    override suspend fun countSuccessfulSessions(): Int = 0
    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int = 0
}

private class RecordingBlockedAppsAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }

    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
    override fun trackFocusSummaryShareTapped(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    override fun trackFocusSummaryShareSheetOpened(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    override fun trackFocusSummaryShareFailed(periodType: String, reason: String) = Unit
}
