package com.uiery.keep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

internal const val MobileAdsDeferredStartupDelayMillis = 1_500L
internal const val FcmTokenDeferredStartupDelayMillis = MobileAdsDeferredStartupDelayMillis

internal class MobileAdsInitializationState {
    private val mutableIsInitialized = MutableStateFlow(false)
    private val initializationStarted = AtomicBoolean(false)

    val isInitialized = mutableIsInitialized.asStateFlow()

    fun tryStartInitialization(): Boolean = initializationStarted.compareAndSet(false, true)

    fun markInitialized() {
        mutableIsInitialized.value = true
    }
}

internal val MobileAdsInitialization = MobileAdsInitializationState()

internal fun shouldStartMobileAdsForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed

internal fun shouldFetchFcmTokenForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed
