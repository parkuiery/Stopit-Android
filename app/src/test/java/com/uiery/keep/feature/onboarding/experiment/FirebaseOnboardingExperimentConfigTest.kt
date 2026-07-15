package com.uiery.keep.feature.onboarding.experiment

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class FirebaseOnboardingExperimentConfigTest {
    @Test
    fun configuresTheApprovedControlSafeDefaults() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)

        FirebaseOnboardingExperimentConfig(remoteConfig)

        verify(remoteConfig).setDefaultsAsync(
            mapOf<String, Any>(
                "onboarding_promise_coach_percent" to 0L,
                "onboarding_promise_coach_new_assignment_enabled" to false,
                "onboarding_promise_coach_emergency_disabled" to false,
            ),
        )
    }

    @Test
    fun mapsAllRemoteValuesIntoAReadableSnapshot() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.getLong("onboarding_promise_coach_percent")).thenReturn(35L)
        `when`(remoteConfig.getBoolean("onboarding_promise_coach_new_assignment_enabled")).thenReturn(true)
        `when`(remoteConfig.getBoolean("onboarding_promise_coach_emergency_disabled")).thenReturn(true)

        val snapshot = FirebaseOnboardingExperimentConfig(remoteConfig).snapshot()

        assertEquals(
            OnboardingExperimentSnapshot(
                treatmentPercent = 35,
                newAssignmentEnabled = true,
                emergencyDisabled = true,
                remoteReadable = true,
            ),
            snapshot,
        )
    }

    @Test
    fun exceptionReadingPercentReturnsACompleteUnreadableSnapshot() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.getLong("onboarding_promise_coach_percent"))
            .thenThrow(IllegalStateException("percent unavailable"))

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun exceptionReadingAssignmentSwitchReturnsACompleteUnreadableSnapshot() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.getLong("onboarding_promise_coach_percent")).thenReturn(100L)
        `when`(remoteConfig.getBoolean("onboarding_promise_coach_new_assignment_enabled"))
            .thenThrow(IllegalStateException("assignment switch unavailable"))

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun exceptionReadingEmergencySwitchReturnsACompleteUnreadableSnapshot() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.getLong("onboarding_promise_coach_percent")).thenReturn(100L)
        `when`(remoteConfig.getBoolean("onboarding_promise_coach_new_assignment_enabled")).thenReturn(true)
        `when`(remoteConfig.getBoolean("onboarding_promise_coach_emergency_disabled"))
            .thenThrow(IllegalStateException("emergency switch unavailable"))

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }
}
