package com.uiery.keep.analytics

import android.app.Activity
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.OnPaidEventListener
import com.uiery.keep.MobileAdsInitialization
import com.uiery.keep.util.findActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

internal const val AdBannerImpressionEvent = "ad_banner_impression"
internal const val AdBannerClickEvent = "ad_banner_click"
internal const val AdBannerRevenueEvent = "ad_banner_revenue"
internal val MetaCompatibleBannerAdSize: AdSize = AdSize.BANNER

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

/**
 * 배너와 바로 위 콘텐츠 사이의 여백.
 *
 * 하단 고정 배너는 앱 콘텐츠와 맞닿아 두 영역이 한 덩어리로 읽힌다. 여백은 배너가 소유해서
 * 화면마다 값이 갈리지 않게 한다. 호출부에서 따로 여백을 주면 두 번 들어간다.
 */
internal val BannerAdContentSeparation = 16.dp

@Composable
internal fun TrackedBannerAd(
    modifier: Modifier = Modifier,
    metadata: AdPlacementMetadata,
    /** 배너가 화면 위쪽에 놓이는 자리(BlockScreen)는 위 여백이 필요 없으므로 0으로 끈다. */
    contentSeparation: Dp = BannerAdContentSeparation,
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
        contentSeparation = contentSeparation,
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
    contentSeparation: Dp = BannerAdContentSeparation,
    adUnitId: String,
    onAdImpression: (() -> Unit)? = null,
    onAdClick: (() -> Unit)? = null,
    onAdRevenuePaid: ((AdValue) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isInspectionMode = LocalInspectionMode.current
    val isMobileAdsInitialized by MobileAdsInitialization.isInitialized.collectAsStateWithLifecycle()

    Box(
        // 여백을 height 앞에 걸어야 배너 자체는 규격 높이를 그대로 쓴다. 순서를 바꾸면
        // 배너가 여백만큼 눌려 광고 뷰 크기가 어긋난다.
        modifier = modifier
            .fillMaxWidth()
            .padding(top = contentSeparation)
            .height(MetaCompatibleBannerAdSize.height.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (
            shouldLoadBannerAd(
                isInspectionMode = isInspectionMode,
                hasActivityContext = activity != null,
                isMobileAdsInitialized = isMobileAdsInitialized,
            )
        ) {
            LoadedBannerAd(
                activity = requireNotNull(activity),
                adUnitId = adUnitId,
                onAdImpression = onAdImpression,
                onAdClick = onAdClick,
                onAdRevenuePaid = onAdRevenuePaid,
            )
        }
    }
}

internal fun shouldLoadBannerAd(
    isInspectionMode: Boolean,
    hasActivityContext: Boolean,
    isMobileAdsInitialized: Boolean,
): Boolean = !isInspectionMode && hasActivityContext && isMobileAdsInitialized

@Composable
private fun LoadedBannerAd(
    activity: Activity,
    adUnitId: String,
    onAdImpression: (() -> Unit)?,
    onAdClick: (() -> Unit)?,
    onAdRevenuePaid: ((AdValue) -> Unit)?,
) {
    val adView = remember(activity, adUnitId) {
        AdView(activity).apply {
            this.adUnitId = adUnitId
            setAdSize(MetaCompatibleBannerAdSize)
        }
    }

    LaunchedEffect(adView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
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
