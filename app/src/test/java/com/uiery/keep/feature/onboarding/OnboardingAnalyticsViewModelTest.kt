package com.uiery.keep.feature.onboarding

import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.feature.onboarding.intro.IntroViewModel
import com.uiery.keep.feature.onboarding.notification.NotificationSettingViewModel
import com.uiery.keep.feature.onboarding.permission.PermissionSettingViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingAnalyticsViewModelTest {

    private val analytics = RecordingKeepAnalytics()

    @Test
    fun introTracksViewAndCompletion() {
        val viewModel = IntroViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onContinue()

        assertEquals(
            listOf(
                AnalyticsCall.StepViewed(OnboardingStepName.INTRO),
                AnalyticsCall.StepCompleted(OnboardingStepName.INTRO),
            ),
            analytics.calls,
        )
    }

    @Test
    fun notificationTracksPermissionOutcomeBeforeCompletion() {
        val viewModel = NotificationSettingViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onPermissionResult(isGranted = false)

        assertEquals(
            listOf(
                AnalyticsCall.StepViewed(OnboardingStepName.NOTIFICATION),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.NOTIFICATIONS,
                    outcome = AnalyticsOutcome.DENIED,
                    stepName = OnboardingStepName.NOTIFICATION,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.NOTIFICATION),
            ),
            analytics.calls,
        )
    }

    @Test
    fun permissionTracksSettingsOpenAndGrantEvents() {
        val viewModel = PermissionSettingViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onPermissionSettingsOpened()
        viewModel.onPermissionGranted()

        assertEquals(
            listOf(
                AnalyticsCall.StepViewed(OnboardingStepName.PERMISSION),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.ACCESSIBILITY,
                    outcome = AnalyticsOutcome.SETTINGS_OPENED,
                    stepName = OnboardingStepName.PERMISSION,
                ),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.ACCESSIBILITY,
                    outcome = AnalyticsOutcome.GRANTED,
                    stepName = OnboardingStepName.PERMISSION,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.PERMISSION),
            ),
            analytics.calls,
        )
    }
}

private sealed interface AnalyticsCall {
    data class Event(val name: String, val params: Map<String, Any?>) : AnalyticsCall
    data class ScreenViewed(val screenName: String) : AnalyticsCall
    data class UserPropertySet(val name: String, val value: String) : AnalyticsCall
    data object FirstOpen : AnalyticsCall
    data class StepViewed(val stepName: String) : AnalyticsCall
    data class StepCompleted(val stepName: String) : AnalyticsCall
    data class PermissionOutcome(
        val permissionName: String,
        val outcome: String,
        val stepName: String?,
    ) : AnalyticsCall
    data class FirstLockConfigured(
        val source: String,
        val selectedAppCount: Int?,
    ) : AnalyticsCall
    data class LockSessionStarted(
        val source: String,
        val isRoutine: Boolean?,
    ) : AnalyticsCall
    data class LockSessionEnded(
        val source: String,
        val endReason: String,
        val isRoutine: Boolean?,
    ) : AnalyticsCall
    data class EmergencyUnlockUsed(
        val source: String,
        val unlockCountRemaining: Int?,
    ) : AnalyticsCall
}

private class RecordingKeepAnalytics : KeepAnalytics {
    val calls = mutableListOf<AnalyticsCall>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        calls += AnalyticsCall.Event(name = name, params = params)
    }

    override fun logScreenView(screenName: String) {
        calls += AnalyticsCall.ScreenViewed(screenName = screenName)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        calls += AnalyticsCall.UserPropertySet(name = name, value = value)
    }

    override fun trackFirstOpen() {
        calls += AnalyticsCall.FirstOpen
    }

    override fun trackOnboardingStepView(stepName: String) {
        calls += AnalyticsCall.StepViewed(stepName = stepName)
    }

    override fun trackOnboardingStepComplete(stepName: String) {
        calls += AnalyticsCall.StepCompleted(stepName = stepName)
    }

    override fun trackPermissionOutcome(
        permissionName: String,
        outcome: String,
        stepName: String?,
    ) {
        calls += AnalyticsCall.PermissionOutcome(
            permissionName = permissionName,
            outcome = outcome,
            stepName = stepName,
        )
    }

    override fun trackFirstLockConfigured(
        source: String,
        selectedAppCount: Int?,
    ) {
        calls += AnalyticsCall.FirstLockConfigured(
            source = source,
            selectedAppCount = selectedAppCount,
        )
    }

    override fun trackLockSessionStart(
        source: String,
        isRoutine: Boolean?,
    ) {
        calls += AnalyticsCall.LockSessionStarted(
            source = source,
            isRoutine = isRoutine,
        )
    }

    override fun trackLockSessionEnd(
        source: String,
        endReason: String,
        isRoutine: Boolean?,
    ) {
        calls += AnalyticsCall.LockSessionEnded(
            source = source,
            endReason = endReason,
            isRoutine = isRoutine,
        )
    }

    override fun trackEmergencyUnlockUsed(
        source: String,
        unlockCountRemaining: Int?,
    ) {
        calls += AnalyticsCall.EmergencyUnlockUsed(
            source = source,
            unlockCountRemaining = unlockCountRemaining,
        )
    }
}
