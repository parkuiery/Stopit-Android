package com.uiery.keep.feature.menu

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.feature.review.FakeDataStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuViewModelTest {
    @Test
    fun initLogsMenuScreenView() {
        val analytics = MenuRecordingKeepAnalytics()

        MenuViewModel(
            dataStore = FakeDataStore(),
            analytics = analytics,
        )

        assertEquals(listOf(KeepAnalyticsScreen.MENU), analytics.screenViews)
    }
}

private class MenuRecordingKeepAnalytics : KeepAnalytics {
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
