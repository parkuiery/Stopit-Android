package com.uiery.keep.feature.onboarding.permission

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionSettingViewModel @Inject constructor(
    private val analytics: KeepAnalytics,
) : ViewModel() {
    fun onStepViewed() {
        analytics.trackOnboardingStepView(OnboardingStepName.PERMISSION)
    }

    fun onPermissionGranted() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.ACCESSIBILITY,
            outcome = AnalyticsOutcome.GRANTED,
            stepName = OnboardingStepName.PERMISSION,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.PERMISSION)
    }

    fun onPermissionSettingsOpened() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.ACCESSIBILITY,
            outcome = AnalyticsOutcome.SETTINGS_OPENED,
            stepName = OnboardingStepName.PERMISSION,
        )
    }
}
