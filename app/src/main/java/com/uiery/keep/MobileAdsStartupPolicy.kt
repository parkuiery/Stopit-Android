package com.uiery.keep

internal const val MobileAdsDeferredStartupDelayMillis = 1_500L

internal fun shouldStartMobileAdsForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed
