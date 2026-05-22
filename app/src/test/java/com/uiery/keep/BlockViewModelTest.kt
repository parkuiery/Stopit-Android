package com.uiery.keep

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockViewModelTest {
    @Test
    fun initLogsBlockScreenView() {
        val analytics = BlockRecordingKeepAnalytics()

        BlockViewModel(
            dataStore = FakeDataStore(),
            emergencyUnlockDao = FakeEmergencyUnlockDao(),
            analytics = analytics,
        )

        assertEquals(listOf("BlockScreen"), analytics.screenViews)
    }
}

private class BlockRecordingKeepAnalytics : KeepAnalytics {
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
}
