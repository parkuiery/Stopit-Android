package com.uiery.keep.analytics

import android.content.Context
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

internal const val AdBannerImpressionEvent = "ad_banner_impression"
internal const val AdBannerClickEvent = "ad_banner_click"
internal const val AdBannerRevenueEvent = "ad_banner_revenue"

internal data class AdPlacementMetadata(
    val screenName: String,
    val screenContext: String,
    val placement: String,
    val adUnitId: String,
    val adFormat: String = "banner",
)

internal data class RevenueMetadata(
    val currencyCode: String,
    val precisionType: String,
    val valueMicros: Long,
)

internal data class MonetizationEvent(
    val name: String,
    val stringParams: Map<String, String>,
    val longParams: Map<String, Long> = emptyMap(),
)

@Composable
internal fun TrackedBannerAd(
    modifier: Modifier = Modifier,
    metadata: AdPlacementMetadata,
) {
    val context = LocalContext.current
    val analyticsBackend = remember(context) { context.findAnalyticsBackend() }

    val tracker = remember(analyticsBackend, metadata) {
        AdMobBannerAnalyticsTracker(
            analyticsBackend = analyticsBackend,
            metadata = metadata,
        )
    }

    AppBannerAd(
        modifier = modifier,
        adUnitId = metadata.adUnitId,
        onAdImpression = tracker::logImpression,
        onAdClick = tracker::logClick,
        onAdRevenuePaid = tracker::logRevenue,
    )
}

@Composable
@RequiresPermission("android.permission.INTERNET")
private fun AppBannerAd(
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

internal fun buildAdImpressionEvent(metadata: AdPlacementMetadata): MonetizationEvent =
    MonetizationEvent(
        name = AdBannerImpressionEvent,
        stringParams = baseParams(metadata),
    )

internal fun buildAdClickEvent(metadata: AdPlacementMetadata): MonetizationEvent =
    MonetizationEvent(
        name = AdBannerClickEvent,
        stringParams = baseParams(metadata),
    )

internal fun buildAdRevenueEvent(
    metadata: AdPlacementMetadata,
    revenueMetadata: RevenueMetadata,
): MonetizationEvent =
    MonetizationEvent(
        name = AdBannerRevenueEvent,
        stringParams = baseParams(metadata) + mapOf(
            "ad_currency" to revenueMetadata.currencyCode,
            "ad_precision_type" to revenueMetadata.precisionType,
        ),
        longParams = mapOf(
            "ad_value_micros" to revenueMetadata.valueMicros,
        ),
    )

private fun baseParams(metadata: AdPlacementMetadata): Map<String, String> =
    mapOf(
        "screen_name" to metadata.screenName,
        "screen_context" to metadata.screenContext,
        "ad_placement" to metadata.placement,
        "ad_format" to metadata.adFormat,
        "ad_unit_id" to metadata.adUnitId,
    )

private class AdMobBannerAnalyticsTracker(
    private val analyticsBackend: AnalyticsBackend,
    private val metadata: AdPlacementMetadata,
) {
    fun logImpression() {
        analyticsBackend.logMonetizationEvent(buildAdImpressionEvent(metadata))
    }

    fun logClick() {
        analyticsBackend.logMonetizationEvent(buildAdClickEvent(metadata))
    }

    fun logRevenue(adValue: AdValue) {
        analyticsBackend.logMonetizationEvent(
            buildAdRevenueEvent(
                metadata = metadata,
                revenueMetadata = RevenueMetadata(
                    currencyCode = adValue.currencyCode,
                    precisionType = adValue.precisionType.toPrecisionTypeName(),
                    valueMicros = adValue.valueMicros,
                ),
            ),
        )
    }
}

private fun AnalyticsBackend.logMonetizationEvent(event: MonetizationEvent) {
    logEvent(
        name = event.name,
        params = event.stringParams + event.longParams,
    )
}

private fun Int.toPrecisionTypeName(): String =
    when (this) {
        AdValue.PrecisionType.ESTIMATED -> "estimated"
        AdValue.PrecisionType.PRECISE -> "precise"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "publisher_provided"
        else -> "unknown"
    }

private fun Context.findAnalyticsBackend(): AnalyticsBackend =
    EntryPointAccessors
        .fromApplication(applicationContext, AnalyticsEntryPoint::class.java)
        .analyticsBackend()

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface AnalyticsEntryPoint {
    fun analyticsBackend(): AnalyticsBackend
}
