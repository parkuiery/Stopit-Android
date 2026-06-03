package com.uiery.keep.feature.emergencyunlocksettings

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.feature.review.FakeDataStore
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyUnlockSettingsViewModelAnalyticsTest {
    @Test
    fun initLogsEmergencyUnlockSettingsScreenView() {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()

        EmergencyUnlockSettingsViewModel(
            settingsStore = EmergencyUnlockSettingsStore(FakeDataStore()),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.EMERGENCY_UNLOCK_SETTINGS), analytics.screenViews)
    }
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
