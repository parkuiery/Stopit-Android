package com.uiery.keep.analytics

internal object AnalyticsBackendFactory {
    fun create(factory: () -> AnalyticsBackend): AnalyticsBackend =
        try {
            factory()
        } catch (_: NoSuchMethodError) {
            NoOpAnalyticsBackend
        }
}

internal object NoOpAnalyticsBackend : AnalyticsBackend {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit
}