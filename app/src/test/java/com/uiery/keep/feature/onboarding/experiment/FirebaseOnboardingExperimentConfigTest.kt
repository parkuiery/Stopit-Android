package com.uiery.keep.feature.onboarding.experiment

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class FirebaseOnboardingExperimentConfigTest {
    @Test
    fun constructingAdapterDoesNotOwnSharedRemoteConfigInitialization() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)

        FirebaseOnboardingExperimentConfig(remoteConfig)

        verifyNoInteractions(remoteConfig)
    }

    @Test
    fun defaultsOnlyValuesWhileFetchIsPendingReturnControlSafeSnapshot() {
        val remoteConfig = remoteConfigWithSources(
            percentSource = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
            assignmentSource = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
            emergencySource = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
        )

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun staticValuesAfterFailedFetchReturnControlSafeSnapshot() {
        val remoteConfig = remoteConfigWithSources(
            percentSource = FirebaseRemoteConfig.VALUE_SOURCE_STATIC,
            assignmentSource = FirebaseRemoteConfig.VALUE_SOURCE_STATIC,
            emergencySource = FirebaseRemoteConfig.VALUE_SOURCE_STATIC,
        )

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun anyNonRemoteValueMakesTheEntireSnapshotUnreadable() {
        val remoteConfig = remoteConfigWithSources(
            percentSource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            assignmentSource = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
            emergencySource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
        )

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun cachedActivatedRemoteValuesRemainReadableDuringRefreshFailureOrPendingFetch() {
        val remoteConfig = remoteConfigWithSources(
            percentSource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            assignmentSource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            emergencySource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            percent = 35L,
            assignmentEnabled = true,
            emergencyDisabled = true,
        )

        assertEquals(
            OnboardingExperimentSnapshot(
                treatmentPercent = 35,
                newAssignmentEnabled = true,
                emergencyDisabled = true,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    @Test
    fun remoteValueConversionFailureReturnsCompleteControlSafeSnapshot() {
        val remoteConfig = remoteConfigWithSources(
            percentSource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            assignmentSource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            emergencySource = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
        )
        `when`(remoteConfig.getValue(PERCENT_KEY).asLong())
            .thenThrow(IllegalArgumentException("invalid percentage"))

        assertEquals(
            OnboardingExperimentSnapshot(),
            FirebaseOnboardingExperimentConfig(remoteConfig).snapshot(),
        )
    }

    private fun remoteConfigWithSources(
        percentSource: Int,
        assignmentSource: Int,
        emergencySource: Int,
        percent: Long = 0L,
        assignmentEnabled: Boolean = false,
        emergencyDisabled: Boolean = false,
    ): FirebaseRemoteConfig {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        val percentValue = remoteValue(source = percentSource, longValue = percent)
        val assignmentValue = remoteValue(source = assignmentSource, booleanValue = assignmentEnabled)
        val emergencyValue = remoteValue(source = emergencySource, booleanValue = emergencyDisabled)
        `when`(remoteConfig.getValue(PERCENT_KEY)).thenReturn(percentValue)
        `when`(remoteConfig.getValue(ASSIGNMENT_KEY)).thenReturn(assignmentValue)
        `when`(remoteConfig.getValue(EMERGENCY_KEY)).thenReturn(emergencyValue)
        return remoteConfig
    }

    private fun remoteValue(
        source: Int,
        longValue: Long? = null,
        booleanValue: Boolean? = null,
    ): FirebaseRemoteConfigValue = mock(FirebaseRemoteConfigValue::class.java).also { value ->
        `when`(value.source).thenReturn(source)
        longValue?.let { `when`(value.asLong()).thenReturn(it) }
        booleanValue?.let { `when`(value.asBoolean()).thenReturn(it) }
    }

    private companion object {
        const val PERCENT_KEY = "onboarding_promise_coach_percent"
        const val ASSIGNMENT_KEY = "onboarding_promise_coach_new_assignment_enabled"
        const val EMERGENCY_KEY = "onboarding_promise_coach_emergency_disabled"
    }
}
