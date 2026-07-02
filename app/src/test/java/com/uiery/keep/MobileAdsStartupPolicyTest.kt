package com.uiery.keep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAdsStartupPolicyTest {

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
