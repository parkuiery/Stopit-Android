package com.uiery.keep.feature.review

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class FirebaseReviewRemoteConfigTest {
    @Test
    fun constructingAdapterDoesNotOwnSharedRemoteConfigInitialization() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)

        FirebaseReviewRemoteConfig(remoteConfig)

        verifyNoInteractions(remoteConfig)
    }

    @Test
    fun readsTheActivatedReviewFlag() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)
        `when`(remoteConfig.getBoolean("in_app_review_enabled")).thenReturn(true)

        assertTrue(FirebaseReviewRemoteConfig(remoteConfig).isEnabled())
    }

    @Test
    fun sharedInitializerOwnsOneCompleteDefaultsMapAndOneFetch() {
        val remoteConfig = mock(FirebaseRemoteConfig::class.java)

        val initialized = FirebaseRemoteConfigInitializer.initialize(remoteConfig)

        assertSame(remoteConfig, initialized)
        verify(remoteConfig, times(1)).setConfigSettingsAsync(any(FirebaseRemoteConfigSettings::class.java))
        verify(remoteConfig, times(1)).setDefaultsAsync(
            mapOf<String, Any>(
                "in_app_review_enabled" to true,
                "onboarding_variant" to "control",
            ),
        )
        verify(remoteConfig, times(1)).fetchAndActivate()
    }
}
