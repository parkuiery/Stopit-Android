package com.uiery.keep.feature.onboarding.notification

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationSettingViewModel @Inject constructor(
    private val analytics: KeepAnalytics,
) : ViewModel() {
    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_NOTIFICATION)
        analytics.trackOnboardingStepView(OnboardingStepName.NOTIFICATION)
    }

    fun onPermissionSettingsOpened() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.SETTINGS_OPENED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
    }

    fun onPermissionDenied() {
        trackNotificationPermissionDenied()
    }

    fun onPermissionDeniedAndContinue() {
        trackNotificationPermissionDenied()
        analytics.trackOnboardingStepComplete(OnboardingStepName.NOTIFICATION)
    }

    private fun trackNotificationPermissionDenied() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.DENIED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
    }

    fun onPermissionGranted() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.GRANTED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.NOTIFICATION)
    }
}
