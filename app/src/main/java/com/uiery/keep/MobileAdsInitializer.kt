package com.uiery.keep

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.uiery.keep.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun ComponentActivity.deferMobileAdsInitialization() {
    window.decorView.post {
        lifecycleScope.launch {
            delay(MobileAdsDeferredStartupDelayMillis)
            if (
                shouldStartMobileAdsForActivity(isFinishing, isDestroyed) &&
                MobileAdsInitialization.tryStartInitialization()
            ) {
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
        }
    }
}

private const val MobileAdsLogTag = "MobileAds"
