package com.uiery.keep.feature.lockhistory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSummarySharePayloadTest {
    @Test
    fun weeklySummaryWithSessionsBuildsPrivacySafeShareTextFromProvider() {
        val provider = RecordingFocusSummaryShareTextProvider(
            text = "I protected focus 3 times for 2 hours 10 minutes this week.\nhttps://play.google.com/store/apps/details?id=com.uiery.keep",
        )

        val payload = buildFocusSummarySharePayload(
            periodType = PeriodType.WEEK,
            sessionCount = 3,
            totalDurationMillis = 130 * 60 * 1000L,
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.uiery.keep",
            textProvider = provider,
        )

        requireNotNull(payload)
        assertEquals("week", payload.periodType)
        assertEquals("2_3", payload.sessionCountBucket)
        assertEquals("120_239", payload.durationMinutesBucket)
        assertEquals(
            FocusSummaryShareTextRequest(
                sessionCount = 3,
                durationMinutes = 130L,
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.uiery.keep",
            ),
            provider.requests.single(),
        )
        assertTrue(payload.text.contains("3 times"))
        assertTrue(payload.text.contains("2 hours 10 minutes"))
        assertTrue(payload.text.contains("https://play.google.com/store/apps/details?id=com.uiery.keep"))
        assertFalse(payload.text.contains("com.example"))
        assertFalse(payload.text.contains("lockedApps"))
        assertFalse(payload.text.contains("2026-"))
    }

    @Test
    fun emptyOrNonWeeklySummaryDoesNotBuildSharePayload() {
        assertNull(
            buildFocusSummarySharePayload(
                periodType = PeriodType.WEEK,
                sessionCount = 0,
                totalDurationMillis = 30 * 60 * 1000L,
                textProvider = RecordingFocusSummaryShareTextProvider(),
            ),
        )
        assertNull(
            buildFocusSummarySharePayload(
                periodType = PeriodType.WEEK,
                sessionCount = 1,
                totalDurationMillis = 0L,
                textProvider = RecordingFocusSummaryShareTextProvider(),
            ),
        )
        assertNull(
            buildFocusSummarySharePayload(
                periodType = PeriodType.MONTH,
                sessionCount = 3,
                totalDurationMillis = 130 * 60 * 1000L,
                textProvider = RecordingFocusSummaryShareTextProvider(),
            ),
        )
    }

    @Test
    fun bucketBoundariesAreStableForAnalytics() {
        assertEquals("1", focusSummarySessionCountBucket(1))
        assertEquals("2_3", focusSummarySessionCountBucket(2))
        assertEquals("2_3", focusSummarySessionCountBucket(3))
        assertEquals("4_6", focusSummarySessionCountBucket(4))
        assertEquals("4_6", focusSummarySessionCountBucket(6))
        assertEquals("7_plus", focusSummarySessionCountBucket(7))

        assertEquals("1_29", focusSummaryDurationMinutesBucket(1))
        assertEquals("1_29", focusSummaryDurationMinutesBucket(29))
        assertEquals("30_59", focusSummaryDurationMinutesBucket(30))
        assertEquals("60_119", focusSummaryDurationMinutesBucket(119))
        assertEquals("120_239", focusSummaryDurationMinutesBucket(120))
        assertEquals("240_plus", focusSummaryDurationMinutesBucket(240))
    }
}

private class RecordingFocusSummaryShareTextProvider(
    private val text: String = "share text",
) : FocusSummaryShareTextProvider {
    val requests = mutableListOf<FocusSummaryShareTextRequest>()

    override fun buildText(request: FocusSummaryShareTextRequest): String {
        requests += request
        return text
    }
}
