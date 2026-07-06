package com.uiery.keep.analytics

/**
 * Fans out every analytics call to each delegate backend.
 *
 * Lets the single [AnalyticsBackend] composition point dual-write to Firebase (full
 * event catalog) and Amplitude (allowlisted subset) without any `trackXxx` call site
 * knowing more than one backend exists. A failure in one backend never prevents the
 * others from receiving the event.
 */
internal class CompositeAnalyticsBackend(
    private val backends: List<AnalyticsBackend>,
) : AnalyticsBackend {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        backends.forEach { backend -> runCatching { backend.logEvent(name, params) } }
    }

    override fun logScreenView(screenName: String) {
        backends.forEach { backend -> runCatching { backend.logScreenView(screenName) } }
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        backends.forEach { backend -> runCatching { backend.setUserProperty(name, value) } }
    }
}
