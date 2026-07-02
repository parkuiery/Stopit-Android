package com.uiery.keep.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseScreenViewPayloadTest {
    @Test
    fun screenViewPayloadIncludesStableScreenNameAndScreenClass() {
        val payload = FirebaseScreenViewPayload.fromScreenName(KeepAnalyticsScreen.HOME)

        assertEquals(
            mapOf(
                FirebaseAnalytics.Param.SCREEN_NAME to KeepAnalyticsScreen.HOME,
                FirebaseAnalytics.Param.SCREEN_CLASS to KeepAnalyticsScreen.HOME,
            ),
            payload,
        )
    }
}
