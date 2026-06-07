package com.uiery.keep.feature.emergencyunlocksettings

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyUnlockSettingsViewModelAnalyticsTest {
    @Test
    fun initLogsEmergencyUnlockSettingsScreenView() {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()

        val dataStore = FakeDataStore()
        EmergencyUnlockSettingsViewModel(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
                settingsStore = EmergencyUnlockSettingsStore(dataStore),
                blockingStateStore = BlockingStateStore(dataStore),
                repository = EmergencyUnlockRepository(RecordingEmergencyUnlockSettingsDao()),
                analytics = analytics,
            ),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.EMERGENCY_UNLOCK_SETTINGS), analytics.screenViews)
    }
}

private class RecordingEmergencyUnlockSettingsDao : EmergencyUnlockDao {
    override suspend fun insert(entity: EmergencyUnlockEntity) = Unit

    override fun fetchByDateRange(start: Long, end: Long): Flow<List<EmergencyUnlockEntity>> = emptyFlow()

    override suspend fun countToday(todayStart: Long): Int = 0

    override suspend fun countSince(timestampMillis: Long): Int = 0
}

private class RecordingEmergencyUnlockSettingsAnalytics : KeepAnalytics {
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
