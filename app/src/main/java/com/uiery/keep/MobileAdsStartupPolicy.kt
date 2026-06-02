package com.uiery.keep

internal const val MobileAdsDeferredStartupDelayMillis = 1_500L
internal const val FcmTokenDeferredStartupDelayMillis = MobileAdsDeferredStartupDelayMillis

internal fun shouldStartMobileAdsForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed

internal fun shouldFetchFcmTokenForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed
