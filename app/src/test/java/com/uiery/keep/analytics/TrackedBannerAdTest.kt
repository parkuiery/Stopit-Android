package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedBannerAdTest {

    private val metadata = AdPlacementMetadata(
        screenName = "RoutineScreen",
        screenContext = "empty_state",
        placement = "routine_empty_bottom",
        adUnitId = "ca-app-pub-1234567890/1234567890",
    )

    @Test
    fun `buildAdImpressionEvent includes screen and placement metadata`() {
        val event = buildAdImpressionEvent(metadata)

        assertEquals(AdImpressionEvent, event.name)
        assertEquals("RoutineScreen", event.stringParams["screen_name"])
        assertEquals("empty_state", event.stringParams["screen_context"])
        assertEquals("routine_empty_bottom", event.stringParams["ad_placement"])
        assertEquals("banner", event.stringParams["ad_format"])
        assertEquals("ca-app-pub-1234567890/1234567890", event.stringParams["ad_unit_id"])
        assertTrue(event.longParams.isEmpty())
    }

    @Test
    fun `buildAdRevenueEvent includes revenue metadata`() {
        val event = buildAdRevenueEvent(
            metadata = metadata,
            revenueMetadata = RevenueMetadata(
                currencyCode = "USD",
                precisionType = "estimated",
                valueMicros = 125000L,
            ),
        )

        assertEquals(AdRevenueEvent, event.name)
        assertEquals("USD", event.stringParams["ad_currency"])
        assertEquals("estimated", event.stringParams["ad_precision_type"])
        assertEquals(125000L, event.longParams["ad_value_micros"])
    }
}
