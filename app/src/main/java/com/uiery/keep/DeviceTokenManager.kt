package com.uiery.keep

import com.uiery.keep.analytics.AnalyticsDeviceRegistrationSkipReason
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.LocalDeviceDataStore
import javax.inject.Inject

class DeviceTokenManager @Inject constructor(
    private val localDeviceDataStore: LocalDeviceDataStore,
    private val analytics: KeepAnalytics,
) {
    suspend fun saveDeviceToken(deviceToken: String) {
        localDeviceDataStore.saveDeviceToken(deviceToken)
        analytics.trackFcmTokenCaptured()
        analytics.trackDeviceRegistrationAttempted()
        if (deviceToken.isBlank()) {
            analytics.trackDeviceRegistrationSkipped(AnalyticsDeviceRegistrationSkipReason.MISSING_FCM_TOKEN)
        } else {
            analytics.trackDeviceRegistrationSkipped(AnalyticsDeviceRegistrationSkipReason.BACKEND_REMOVED)
        }
    }
}
