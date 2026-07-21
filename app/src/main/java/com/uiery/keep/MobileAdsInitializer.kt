package com.uiery.keep

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.FormError
import com.uiery.keep.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ComponentActivity.deferMobileAdsInitialization() {
    window.decorView.post {
        lifecycleScope.launch {
            delay(MobileAdsDeferredStartupDelayMillis)
            if (
                shouldStartMobileAdsForActivity(isFinishing, isDestroyed) &&
                MobileAdsInitialization.tryStartConsentGathering()
            ) {
                val consentManager = GoogleMobileAdsConsentManager.getInstance(applicationContext)
                consentManager.gatherConsent(this@deferMobileAdsInitialization) { error ->
                    logConsentError(error)
                    updatePrivacyOptionsRequirement(consentManager)
                    initializeMobileAdsIfAllowed(consentManager)
                    MobileAdsInitialization.completeConsentGathering()
                }
                updatePrivacyOptionsRequirement(consentManager)
                initializeMobileAdsIfAllowed(consentManager)
            }
        }
    }
}

internal fun showMobileAdsPrivacyOptions(
    activity: Activity,
    onError: (String) -> Unit,
) {
    val consentManager = GoogleMobileAdsConsentManager.getInstance(activity.applicationContext)
    consentManager.showPrivacyOptionsForm(activity) { error ->
        logConsentError(error)
        updatePrivacyOptionsRequirement(consentManager)
        error?.message?.let(onError)
    }
}

private fun ComponentActivity.initializeMobileAdsIfAllowed(
    consentManager: GoogleMobileAdsConsentManager,
) {
    if (!MobileAdsInitialization.tryStartInitialization(consentManager.canRequestAds)) return

    MobileAds.initialize(applicationContext) { initializationStatus ->
        initializationStatus.adapterStatusMap
            .toSortedMap()
            .forEach { (adapterName, adapterStatus) ->
                AppLogger.debug(
                    tag = MobileAdsLogTag,
                    message = "adapter=$adapterName " +
                        "state=${adapterStatus.initializationState} " +
                        "latencyMs=${adapterStatus.latency}",
                )
            }
        MobileAdsInitialization.markInitialized()
    }
}

private fun updatePrivacyOptionsRequirement(consentManager: GoogleMobileAdsConsentManager) {
    MobileAdsPrivacyOptions.update(consentManager.isPrivacyOptionsRequired)
}

private fun logConsentError(error: FormError?) {
    if (error == null) return
    AppLogger.debug(
        tag = MobileAdsLogTag,
        message = "consentErrorCode=${error.errorCode} message=${error.message}",
    )
}

private const val MobileAdsLogTag = "MobileAds"
