package com.uiery.kds

import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.OnPaidEventListener

private const val TEST_AD_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
@RequiresPermission("android.permission.INTERNET")
fun KeepBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String,
    onAdImpression: (() -> Unit)? = null,
    onAdClick: (() -> Unit)? = null,
    onAdRevenuePaid: ((AdValue) -> Unit)? = null,
) {
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidth)
    val adView = remember {
        AdView(context).apply {
            this.adUnitId = adUnitId
            this.setAdSize(adSize)
        }
    }

    if (!LocalInspectionMode.current) {
        LaunchedEffect(adView) {
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        }
    }

    DisposableEffect(adView, onAdImpression, onAdClick, onAdRevenuePaid) {
        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                onAdClick?.invoke()
            }

            override fun onAdImpression() {
                onAdImpression?.invoke()
            }
        }
        adView.onPaidEventListener = onAdRevenuePaid?.let { callback ->
            OnPaidEventListener { adValue -> callback(adValue) }
        }

        onDispose {
            adView.adListener = object : AdListener() {}
            adView.onPaidEventListener = null
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier.wrapContentSize(),
        factory = {
            adView
        },
    )

    LifecycleResumeEffect(adView) {
        adView.resume()
        onPauseOrDispose { adView.pause() }
    }
}
