package com.uiery.keep.feature.onboarding.permission

import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionSettingViewModel @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : ViewModel() {
    fun allowPermissionClick() {
        analytics.logEvent("allow_permission_click", null)
    }

    fun notificationSettingComplete() {
        analytics.logEvent("notification_setting_complete", null)
    }
}