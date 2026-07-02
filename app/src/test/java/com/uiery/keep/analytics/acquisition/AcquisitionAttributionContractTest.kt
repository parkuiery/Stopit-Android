package com.uiery.keep.analytics.acquisition

import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.KeepAnalyticsParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionAttributionContractTest {

    @Test
    fun fullUtmReferrerNormalizesPrivacySafeAnalyticsParams() {
        val attribution = AcquisitionAttributionParser.parse(
            rawReferrer = "utm_source=discord&utm_medium=social&utm_campaign=aso_baseline&search_term=john@example.com&click_id=abc123",
            lookupStatus = InstallReferrerLookupStatus.SUCCESS,
            latencyMillis = 812,
        )

        assertEquals(ReferrerStatus.SUCCESS, attribution.referrerStatus)
        assertEquals(UtmSourceType.DISCORD, attribution.utmSourceType)
        assertEquals(UtmMediumType.SOCIAL, attribution.utmMediumType)
        assertEquals(CampaignBucket.ASO_BASELINE, attribution.campaignBucket)
        assertEquals(LinkSurface.DISCORD, attribution.linkSurface)
        assertEquals(LookupLatencyBucket.MS_500_999, attribution.lookupLatencyBucket)

        val params = attribution.toAnalyticsParams()
        assertEquals("success", params[KeepAnalyticsParam.REFERRER_STATUS])
        assertEquals("discord", params[KeepAnalyticsParam.UTM_SOURCE_TYPE])
        assertEquals("social", params[KeepAnalyticsParam.UTM_MEDIUM_TYPE])
        assertEquals("aso_baseline", params[KeepAnalyticsParam.CAMPAIGN_BUCKET])
        assertEquals("discord", params[KeepAnalyticsParam.LINK_SURFACE])
        assertEquals("500_999ms", params[KeepAnalyticsParam.LOOKUP_LATENCY_BUCKET])
        assertFalse(params.values.any { it.toString().contains("john@example.com") })
        assertFalse(params.values.any { it.toString().contains("click_id") })
        assertFalse(params.values.any { it.toString().contains("utm_source=") })
    }

    @Test
    fun missingAndUnavailableReferrersProduceExplicitNoneBuckets() {
        val missing = AcquisitionAttributionParser.parse(
            rawReferrer = "",
            lookupStatus = InstallReferrerLookupStatus.SUCCESS,
            latencyMillis = null,
        )
        val unavailable = AcquisitionAttributionParser.parse(
            rawReferrer = null,
            lookupStatus = InstallReferrerLookupStatus.UNAVAILABLE,
            latencyMillis = 2_100,
        )

        assertEquals(ReferrerStatus.MISSING, missing.referrerStatus)
        assertEquals(UtmSourceType.NONE, missing.utmSourceType)
        assertEquals(UtmMediumType.NONE, missing.utmMediumType)
        assertEquals(CampaignBucket.NONE, missing.campaignBucket)
        assertEquals(LookupLatencyBucket.NOT_MEASURED, missing.lookupLatencyBucket)

        assertEquals(ReferrerStatus.UNAVAILABLE, unavailable.referrerStatus)
        assertEquals(UtmSourceType.NONE, unavailable.utmSourceType)
        assertEquals(UtmMediumType.NONE, unavailable.utmMediumType)
        assertEquals(CampaignBucket.NONE, unavailable.campaignBucket)
        assertEquals(LookupLatencyBucket.MS_2000_PLUS, unavailable.lookupLatencyBucket)
    }

    @Test
    fun malformedOrUnknownUtmNeverLeaksRawCampaign() {
        val attribution = AcquisitionAttributionParser.parse(
            rawReferrer = "utm_source=some_private_group&utm_medium=weird_medium&utm_campaign=VIPUserLaunch2026",
            lookupStatus = InstallReferrerLookupStatus.SUCCESS,
            latencyMillis = 20,
        )

        assertEquals(ReferrerStatus.SUCCESS, attribution.referrerStatus)
        assertEquals(UtmSourceType.UNKNOWN, attribution.utmSourceType)
        assertEquals(UtmMediumType.UNKNOWN, attribution.utmMediumType)
        assertEquals(CampaignBucket.OTHER, attribution.campaignBucket)
        assertFalse(attribution.toAnalyticsParams().values.any { it.toString().contains("VIPUserLaunch2026") })
    }

    @Test
    fun campaignLinkBuilderRequiresSafeSluggedUtmFields() {
        val link = CampaignLinkBuilder.buildPlayStoreUrl(
            packageName = "com.uiery.keep",
            source = UtmSourceType.DISCORD,
            medium = UtmMediumType.SOCIAL,
            campaign = CampaignBucket.ASO_BASELINE,
            surface = LinkSurface.DISCORD,
        )

        assertEquals(
            "https://play.google.com/store/apps/details?id=com.uiery.keep&utm_source=discord&utm_medium=social&utm_campaign=aso_baseline&utm_content=discord",
            link,
        )
        assertTrue(CampaignLinkBuilder.isSafeSlug("aso_baseline"))
        assertFalse(CampaignLinkBuilder.isSafeSlug("대표님 launch"))
    }

    @Test
    fun analyticsEventNameMatchesDictionaryContract() {
        assertEquals("install_referrer_attribution_checked", KeepAnalyticsEvent.INSTALL_REFERRER_ATTRIBUTION_CHECKED)
    }
}
