package com.uiery.keep.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdValue
import com.uiery.kds.KeepBannerAd
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

internal const val AdImpressionEvent = "ad_impression"
internal const val AdClickEvent = "ad_click"
internal const val AdRevenueEvent = "ad_revenue"

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

    KeepBannerAd(
        modifier = modifier,
        adUnitId = metadata.adUnitId,
        onAdImpression = tracker::logImpression,
        onAdClick = tracker::logClick,
        onAdRevenuePaid = tracker::logRevenue,
    )
}

internal fun buildAdImpressionEvent(metadata: AdPlacementMetadata): MonetizationEvent =
    MonetizationEvent(
        name = AdImpressionEvent,
        stringParams = baseParams(metadata),
    )

internal fun buildAdClickEvent(metadata: AdPlacementMetadata): MonetizationEvent =
    MonetizationEvent(
        name = AdClickEvent,
        stringParams = baseParams(metadata),
    )

internal fun buildAdRevenueEvent(
    metadata: AdPlacementMetadata,
    revenueMetadata: RevenueMetadata,
): MonetizationEvent =
    MonetizationEvent(
        name = AdRevenueEvent,
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
