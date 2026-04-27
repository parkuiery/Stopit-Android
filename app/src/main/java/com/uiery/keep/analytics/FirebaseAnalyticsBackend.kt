package com.uiery.keep.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsBackend
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsBackend {
        override fun logEvent(
            name: String,
            params: Map<String, Any?>,
        ) {
            firebaseAnalytics.logEvent(name, params.toBundle())
        }

        override fun logScreenView(screenName: String) {
            firebaseAnalytics.logEvent(
                FirebaseAnalytics.Event.SCREEN_VIEW,
                Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                },
            )
        }

        override fun setUserProperty(
            name: String,
            value: String,
        ) {
            firebaseAnalytics.setUserProperty(name, value)
        }
    }

private fun Map<String, Any?>.toBundle(): Bundle? {
    if (isEmpty()) return null

    return Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> putString(key, value)
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                is Boolean -> putString(key, value.toString())
                else -> putString(key, value.toString())
            }
        }
    }
}
