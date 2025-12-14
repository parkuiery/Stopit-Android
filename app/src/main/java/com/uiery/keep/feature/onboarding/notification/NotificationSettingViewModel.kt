package com.uiery.keep.feature.onboarding.notification

import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NotificationSettingViewModel @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : ViewModel() {
    fun notificationSettingCompleteAnalytics() {
        analytics.logEvent("notification_setting_complete", null)
    }
}