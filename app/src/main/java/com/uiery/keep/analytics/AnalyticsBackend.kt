package com.uiery.keep.analytics

interface AnalyticsBackend {
    fun logEvent(
        name: String,
        params: Map<String, Any?> = emptyMap(),
    )

    fun logScreenView(screenName: String)

    fun setUserProperty(
        name: String,
        value: String,
    )
}
