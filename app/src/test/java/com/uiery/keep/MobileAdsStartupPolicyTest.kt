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
}
