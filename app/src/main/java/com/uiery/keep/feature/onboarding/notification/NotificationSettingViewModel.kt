package com.uiery.keep.feature.onboarding.notification

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationSettingViewModel @Inject constructor(
    private val analytics: KeepAnalytics,
) : ViewModel() {
    fun onStepViewed() {
        analytics.trackOnboardingStepView(OnboardingStepName.NOTIFICATION)
    }

    fun onPermissionResult(isGranted: Boolean) {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = if (isGranted) AnalyticsOutcome.GRANTED else AnalyticsOutcome.DENIED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.NOTIFICATION)
    }
}
