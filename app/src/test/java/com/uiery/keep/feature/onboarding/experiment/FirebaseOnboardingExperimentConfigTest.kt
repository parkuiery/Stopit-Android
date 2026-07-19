package com.uiery.keep.feature.onboarding.experiment

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
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
    fun remoteTreatmentResolvesReadableTreatment() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "promise_coach_v1",
        )

        assertEquals(
            OnboardingExperimentResolution(
                variant = OnboardingVariant.PromiseCoachV1,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun remoteControlResolvesReadableControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "control",
        )

        assertEquals(
            OnboardingExperimentResolution(
                variant = OnboardingVariant.Control,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun failedFetchUsesCachedRemoteTreatment() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            fetchTask = Tasks.forException(IllegalStateException("network")),
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "promise_coach_v1",
        )

        assertEquals(
            OnboardingExperimentResolution(
                variant = OnboardingVariant.PromiseCoachV1,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun failedFetchUsesCachedRemoteControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            fetchTask = Tasks.forException(IllegalStateException("network")),
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "control",
        )

        assertEquals(
            OnboardingExperimentResolution(
                variant = OnboardingVariant.Control,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun failedFetchWithoutActiveRemoteResolvesUnreadableControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            fetchTask = Tasks.forException(IllegalStateException("network")),
            source = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
            variant = "control",
        )

        assertEquals(
            OnboardingExperimentResolution(),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun defaultOnlyValueResolvesUnreadableControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            source = FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT,
            variant = "control",
        )

        assertEquals(
            OnboardingExperimentResolution(),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun malformedRemoteStringResolvesReadableControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "something_else",
        )

        assertEquals(
            OnboardingExperimentResolution(
                variant = OnboardingVariant.Control,
                remoteReadable = true,
            ),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun remoteValueConversionFailureResolvesUnreadableControl() = runBlocking {
        val remoteConfig = remoteConfigWithVariant(
            source = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE,
            variant = "promise_coach_v1",
        )
        `when`(remoteConfig.getValue(VARIANT_KEY).asString())
            .thenThrow(IllegalArgumentException("invalid variant"))

        assertEquals(
            OnboardingExperimentResolution(),
            FirebaseOnboardingExperimentConfig(remoteConfig).resolve(),
        )
    }

    @Test
    fun cancelledFetchPropagatesCancellationWithoutReadingValue() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.fetchAndActivate()).thenReturn(Tasks.forCanceled())

        assertThrows(CancellationException::class.java) {
            runBlocking {
                FirebaseOnboardingExperimentConfig(remoteConfig).resolve()
            }
        }
        verify(remoteConfig, never()).getValue(VARIANT_KEY)
    }

    private fun remoteConfigWithVariant(
        fetchTask: com.google.android.gms.tasks.Task<Boolean> = Tasks.forResult(true),
        source: Int,
        variant: String,
    ): FirebaseRemoteConfig {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        val variantValue = remoteValue(source = source, stringValue = variant)
        `when`(remoteConfig.fetchAndActivate()).thenReturn(fetchTask)
        `when`(remoteConfig.getValue(VARIANT_KEY)).thenReturn(variantValue)
        return remoteConfig
    }

    private fun remoteValue(
        source: Int,
        stringValue: String,
    ): FirebaseRemoteConfigValue = mock(FirebaseRemoteConfigValue::class.java).also { value ->
        `when`(value.source).thenReturn(source)
        `when`(value.asString()).thenReturn(stringValue)
    }

    private companion object {
        const val VARIANT_KEY = "onboarding_variant"
    }
}
