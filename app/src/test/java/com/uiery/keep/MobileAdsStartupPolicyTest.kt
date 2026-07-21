package com.uiery.keep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAdsStartupPolicyTest {

    @Test
    fun mobileAdsInitializationStateStartsPendingAndBecomesReady() {
        val state = MobileAdsInitializationState()

        assertFalse(state.isInitialized.value)

        state.markInitialized()

        assertTrue(state.isInitialized.value)
    }

    @Test
    fun mobileAdsInitializationRequiresConsentAndStartsOnlyOnceAcrossActivities() {
        val state = MobileAdsInitializationState()

        assertFalse(state.tryStartInitialization(canRequestAds = false))
        assertTrue(state.tryStartInitialization(canRequestAds = true))
        assertFalse(state.tryStartInitialization(canRequestAds = true))
    }

    @Test
    fun consentGatheringStartsOnceAtATimeAndRunsAgainOnTheNextAppLaunch() {
        val state = MobileAdsInitializationState()

        assertTrue(state.tryStartConsentGathering())
        assertFalse(state.tryStartConsentGathering())

        state.completeConsentGathering()

        assertTrue(state.tryStartConsentGathering())
    }

    @Test
    fun privacyOptionsStateReflectsTheLatestConsentRequirement() {
        val state = MobileAdsPrivacyOptionsState()

        assertFalse(state.isRequired.value)

        state.update(isRequired = true)
        assertTrue(state.isRequired.value)

        state.update(isRequired = false)
        assertFalse(state.isRequired.value)
    }

    @Test
    fun mobileAdsInitializationIsDeferredPastActivityOnCreateCriticalPath() {
        assertTrue(
            "MobileAds initialization should be delayed so WebView/Play-services startup work does not run inline during Activity.onCreate",
            MobileAdsDeferredStartupDelayMillis >= 1_000L,
        )
    }

    @Test
    fun destroyedOrFinishingActivityDoesNotInitializeMobileAdsAfterDelay() {
        assertTrue(shouldStartMobileAdsForActivity(isFinishing = false, isDestroyed = false))
        assertFalse(shouldStartMobileAdsForActivity(isFinishing = true, isDestroyed = false))
        assertFalse(shouldStartMobileAdsForActivity(isFinishing = false, isDestroyed = true))
    }

    @Test
    fun fcmTokenFetchUsesTheSameDeferredStartupBoundary() {
        assertTrue(
            "FCM token fetch should also wait until after first-frame startup work instead of running inline during Activity.onCreate",
            FcmTokenDeferredStartupDelayMillis >= 1_000L,
        )
        assertTrue(shouldFetchFcmTokenForActivity(isFinishing = false, isDestroyed = false))
        assertFalse(shouldFetchFcmTokenForActivity(isFinishing = true, isDestroyed = false))
        assertFalse(shouldFetchFcmTokenForActivity(isFinishing = false, isDestroyed = true))
    }
}
