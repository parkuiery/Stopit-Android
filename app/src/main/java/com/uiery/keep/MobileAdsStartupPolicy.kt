package com.uiery.keep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

internal const val MobileAdsDeferredStartupDelayMillis = 1_500L
internal const val FcmTokenDeferredStartupDelayMillis = MobileAdsDeferredStartupDelayMillis

internal class MobileAdsInitializationState {
    private val mutableIsInitialized = MutableStateFlow(false)
    private val consentGatheringStarted = AtomicBoolean(false)
    private val initializationStarted = AtomicBoolean(false)

    val isInitialized = mutableIsInitialized.asStateFlow()

    fun tryStartConsentGathering(): Boolean = consentGatheringStarted.compareAndSet(false, true)

    fun completeConsentGathering() {
        consentGatheringStarted.set(false)
    }

    fun tryStartInitialization(canRequestAds: Boolean): Boolean =
        canRequestAds && initializationStarted.compareAndSet(false, true)

    fun markInitialized() {
        mutableIsInitialized.value = true
    }
}

internal val MobileAdsInitialization = MobileAdsInitializationState()

internal class MobileAdsPrivacyOptionsState {
    private val mutableIsRequired = MutableStateFlow(false)

    val isRequired = mutableIsRequired.asStateFlow()

    fun update(isRequired: Boolean) {
        mutableIsRequired.value = isRequired
    }
}

internal val MobileAdsPrivacyOptions = MobileAdsPrivacyOptionsState()

internal fun shouldStartMobileAdsForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed

internal fun shouldFetchFcmTokenForActivity(
    isFinishing: Boolean,
    isDestroyed: Boolean,
): Boolean = !isFinishing && !isDestroyed
