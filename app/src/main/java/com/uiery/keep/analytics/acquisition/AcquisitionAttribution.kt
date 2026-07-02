package com.uiery.keep.analytics.acquisition

import com.uiery.keep.analytics.KeepAnalyticsParam
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AcquisitionAttributionParser {
    fun parse(
        rawReferrer: String?,
        lookupStatus: InstallReferrerLookupStatus,
        latencyMillis: Long?,
    ): AcquisitionAttribution {
        val fields = parseQuery(rawReferrer)
        val hasReferrer = rawReferrer?.isNotBlank() == true
        val referrerStatus = when {
            lookupStatus == InstallReferrerLookupStatus.UNAVAILABLE -> ReferrerStatus.UNAVAILABLE
            lookupStatus == InstallReferrerLookupStatus.TIMEOUT -> ReferrerStatus.TIMEOUT
            lookupStatus == InstallReferrerLookupStatus.ERROR -> ReferrerStatus.ERROR
            !hasReferrer -> ReferrerStatus.MISSING
            missingRequiredUtm(fields) -> ReferrerStatus.MALFORMED
            else -> ReferrerStatus.SUCCESS
        }
        return AcquisitionAttribution(
            referrerStatus = referrerStatus,
            utmSourceType = fields[UTM_SOURCE]?.let(UtmSourceType::fromRaw) ?: UtmSourceType.NONE,
            utmMediumType = fields[UTM_MEDIUM]?.let(UtmMediumType::fromRaw) ?: UtmMediumType.NONE,
            campaignBucket = fields[UTM_CAMPAIGN]?.let(CampaignBucket::fromRaw) ?: CampaignBucket.NONE,
            linkSurface = fields[UTM_SOURCE]?.let(LinkSurface::fromRaw) ?: LinkSurface.NONE,
            lookupLatencyBucket = LookupLatencyBucket.fromMillis(latencyMillis),
        )
    }

    private fun missingRequiredUtm(fields: Map<String, String>): Boolean {
        if (fields.isEmpty()) return false
        return REQUIRED_UTM_KEYS.any { fields[it].isNullOrBlank() }
    }

    private fun parseQuery(rawReferrer: String?): Map<String, String> {
        if (rawReferrer.isNullOrBlank()) return emptyMap()
        val query = rawReferrer.substringAfter('?', rawReferrer)
        return query
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "")
                if (key.isBlank()) return@mapNotNull null
                val value = part.substringAfter('=', missingDelimiterValue = "")
                decode(key).lowercase() to decode(value).trim()
            }.toMap()
    }

    private fun decode(value: String): String =
        java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private const val UTM_SOURCE = "utm_source"
    private const val UTM_MEDIUM = "utm_medium"
    private const val UTM_CAMPAIGN = "utm_campaign"
    private val REQUIRED_UTM_KEYS = setOf(UTM_SOURCE, UTM_MEDIUM, UTM_CAMPAIGN)
}

data class AcquisitionAttribution(
    val referrerStatus: ReferrerStatus,
    val utmSourceType: UtmSourceType,
    val utmMediumType: UtmMediumType,
    val campaignBucket: CampaignBucket,
    val linkSurface: LinkSurface,
    val lookupLatencyBucket: LookupLatencyBucket,
) {
    fun toAnalyticsParams(): Map<String, String> = mapOf(
        KeepAnalyticsParam.REFERRER_STATUS to referrerStatus.value,
        KeepAnalyticsParam.UTM_SOURCE_TYPE to utmSourceType.value,
        KeepAnalyticsParam.UTM_MEDIUM_TYPE to utmMediumType.value,
        KeepAnalyticsParam.CAMPAIGN_BUCKET to campaignBucket.value,
        KeepAnalyticsParam.LINK_SURFACE to linkSurface.value,
        KeepAnalyticsParam.LOOKUP_LATENCY_BUCKET to lookupLatencyBucket.value,
    )
}

enum class InstallReferrerLookupStatus {
    SUCCESS,
    UNAVAILABLE,
    TIMEOUT,
    ERROR,
}

enum class ReferrerStatus(val value: String) {
    SUCCESS("success"),
    MISSING("missing"),
    UNAVAILABLE("unavailable"),
    TIMEOUT("timeout"),
    ERROR("error"),
    MALFORMED("malformed"),
}

enum class UtmSourceType(val value: String) {
    PLAY_STORE("play_store"),
    DISCORD("discord"),
    WEB("web"),
    QR("qr"),
    PAID_SEARCH("paid_search"),
    COMMUNITY("community"),
    UNKNOWN("unknown"),
    NONE("none"),
    ;

    companion object {
        fun fromRaw(raw: String): UtmSourceType = when (raw.normalized()) {
            "play_store", "google_play", "play" -> PLAY_STORE
            "discord" -> DISCORD
            "web", "website", "homepage" -> WEB
            "qr", "qr_code" -> QR
            "paid_search", "google_ads", "ads", "ad" -> PAID_SEARCH
            "community", "reddit", "x", "twitter" -> COMMUNITY
            "" -> NONE
            else -> UNKNOWN
        }
    }
}

enum class UtmMediumType(val value: String) {
    ORGANIC("organic"),
    SOCIAL("social"),
    REFERRAL("referral"),
    PAID("paid"),
    QR("qr"),
    OWNED("owned"),
    UNKNOWN("unknown"),
    NONE("none"),
    ;

    companion object {
        fun fromRaw(raw: String): UtmMediumType = when (raw.normalized()) {
            "organic" -> ORGANIC
            "social" -> SOCIAL
            "referral", "refer" -> REFERRAL
            "paid", "cpc", "ppc" -> PAID
            "qr", "qr_code" -> QR
            "owned", "email", "newsletter" -> OWNED
            "" -> NONE
            else -> UNKNOWN
        }
    }
}

enum class CampaignBucket(val value: String) {
    ASO_BASELINE("aso_baseline"),
    LAUNCH("launch"),
    REVIEW_PUSH("review_push"),
    ROUTINE_SHARE("routine_share"),
    MANUAL_TEST("manual_test"),
    OTHER("other"),
    NONE("none"),
    ;

    companion object {
        fun fromRaw(raw: String): CampaignBucket = when (raw.normalized()) {
            "aso_baseline" -> ASO_BASELINE
            "launch" -> LAUNCH
            "review_push" -> REVIEW_PUSH
            "routine_share" -> ROUTINE_SHARE
            "manual_test" -> MANUAL_TEST
            "" -> NONE
            else -> OTHER
        }
    }
}

enum class LinkSurface(val value: String) {
    PLAY_STORE_LISTING("play_store_listing"),
    DISCORD("discord"),
    WEBSITE("website"),
    DOCS("docs"),
    QR("qr"),
    AD("ad"),
    UNKNOWN("unknown"),
    NONE("none"),
    ;

    companion object {
        fun fromRaw(raw: String): LinkSurface = when (raw.normalized()) {
            "play_store", "google_play", "play" -> PLAY_STORE_LISTING
            "discord" -> DISCORD
            "web", "website", "homepage" -> WEBSITE
            "docs", "documentation" -> DOCS
            "qr", "qr_code" -> QR
            "paid_search", "google_ads", "ads", "ad" -> AD
            "" -> NONE
            else -> UNKNOWN
        }
    }
}

enum class LookupLatencyBucket(val value: String) {
    MS_0_499("0_499ms"),
    MS_500_999("500_999ms"),
    MS_1000_1999("1000_1999ms"),
    MS_2000_PLUS("2000ms_plus"),
    NOT_MEASURED("not_measured"),
    ;

    companion object {
        fun fromMillis(latencyMillis: Long?): LookupLatencyBucket = when {
            latencyMillis == null -> NOT_MEASURED
            latencyMillis < 500 -> MS_0_499
            latencyMillis < 1_000 -> MS_500_999
            latencyMillis < 2_000 -> MS_1000_1999
            else -> MS_2000_PLUS
        }
    }
}

object CampaignLinkBuilder {
    fun buildPlayStoreUrl(
        packageName: String,
        source: UtmSourceType,
        medium: UtmMediumType,
        campaign: CampaignBucket,
        surface: LinkSurface,
    ): String {
        require(isSafeSlug(packageName.replace('.', '_'))) { "packageName must be slug-safe" }
        require(source != UtmSourceType.UNKNOWN && source != UtmSourceType.NONE) { "source must be explicit" }
        require(medium != UtmMediumType.UNKNOWN && medium != UtmMediumType.NONE) { "medium must be explicit" }
        require(campaign != CampaignBucket.OTHER && campaign != CampaignBucket.NONE) { "campaign must be explicit" }
        require(surface != LinkSurface.UNKNOWN && surface != LinkSurface.NONE) { "surface must be explicit" }
        val params = listOf(
            "id" to packageName,
            "utm_source" to source.value,
            "utm_medium" to medium.value,
            "utm_campaign" to campaign.value,
            "utm_content" to surface.value,
        ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "https://play.google.com/store/apps/details?$params"
    }

    fun isSafeSlug(value: String): Boolean = value.matches(SAFE_SLUG_REGEX)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private val SAFE_SLUG_REGEX = Regex("[a-z0-9_.-]+")
}

private fun String.normalized(): String = trim().lowercase().replace('-', '_')
