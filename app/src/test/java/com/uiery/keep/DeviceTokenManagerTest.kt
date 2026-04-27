package com.uiery.keep

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.LocalDeviceDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTokenManagerTest {
    private val dataStore = RecordingLocalDeviceDataStore()
    private val analytics = RecordingKeepAnalytics()
    private val manager = DeviceTokenManager(dataStore, analytics)

    @Test
    fun tokenCapturePersistsTokenAndSkipsBackendRegistrationWithReason() = runBlocking {
        manager.saveDeviceToken("token-123")

        assertEquals("token-123", dataStore.savedTokens.single())
        assertEquals(
            listOf(
                AnalyticsCall.FcmTokenCaptured,
                AnalyticsCall.DeviceRegistrationAttempted,
                AnalyticsCall.DeviceRegistrationSkipped("backend_removed"),
            ),
            analytics.calls,
        )
    }
}

private class RecordingLocalDeviceDataStore : LocalDeviceDataStore {
    val savedTokens = mutableListOf<String>()

    override suspend fun saveDeviceToken(deviceToken: String) {
        savedTokens += deviceToken
    }
}

private sealed interface AnalyticsCall {
    data object FcmTokenCaptured : AnalyticsCall
    data object DeviceRegistrationAttempted : AnalyticsCall
    data class DeviceRegistrationSkipped(val reason: String) : AnalyticsCall
}

private class RecordingKeepAnalytics : KeepAnalytics {
    val calls = mutableListOf<AnalyticsCall>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(
        permissionName: String,
        outcome: String,
        stepName: String?,
    ) = Unit

    override fun trackFirstLockConfigured(
        source: String,
        selectedAppCount: Int?,
    ) = Unit

    override fun trackLockSessionStart(
        source: String,
        isRoutine: Boolean?,
    ) = Unit

    override fun trackLockSessionEnd(
        source: String,
        endReason: String,
        isRoutine: Boolean?,
    ) = Unit

    override fun trackEmergencyUnlockUsed(
        source: String,
        unlockCountRemaining: Int?,
    ) = Unit

    override fun trackFcmTokenCaptured() {
        calls += AnalyticsCall.FcmTokenCaptured
    }

    override fun trackDeviceRegistrationAttempted() {
        calls += AnalyticsCall.DeviceRegistrationAttempted
    }

    override fun trackDeviceRegistrationSkipped(reason: String) {
        calls += AnalyticsCall.DeviceRegistrationSkipped(reason)
    }
}
