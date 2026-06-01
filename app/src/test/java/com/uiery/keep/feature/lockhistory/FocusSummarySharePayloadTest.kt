package com.uiery.keep.feature.lockhistory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSummarySharePayloadTest {
    @Test
    fun weeklySummaryWithSessionsBuildsPrivacySafeShareText() {
        val payload = buildFocusSummarySharePayload(
            periodType = PeriodType.WEEK,
            sessionCount = 3,
            totalDurationMillis = 130 * 60 * 1000L,
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.uiery.keep",
        )

        requireNotNull(payload)
        assertEquals("week", payload.periodType)
        assertEquals("2_3", payload.sessionCountBucket)
        assertEquals("120_239", payload.durationMinutesBucket)
        assertTrue(payload.text.contains("3번"))
        assertTrue(payload.text.contains("2시간 10분"))
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
            ),
        )
        assertNull(
            buildFocusSummarySharePayload(
                periodType = PeriodType.WEEK,
                sessionCount = 1,
                totalDurationMillis = 0L,
            ),
        )
        assertNull(
            buildFocusSummarySharePayload(
                periodType = PeriodType.MONTH,
                sessionCount = 3,
                totalDurationMillis = 130 * 60 * 1000L,
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
