package com.uiery.keep.analytics

import com.google.firebase.analytics.FirebaseAnalytics

internal object FirebaseScreenViewPayload {
    fun fromScreenName(screenName: String): Map<String, String> =
        mapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName,
            FirebaseAnalytics.Param.SCREEN_CLASS to screenName,
        )
}
