package com.uiery.keep.analytics

/**
 * Forwards only allowlisted events to [delegate].
 *
 * Wraps the Amplitude backend so only high-signal lifecycle/funnel events reach
 * Amplitude, keeping per-user event volume inside the free-tier budget. User
 * properties are always forwarded (they carry no per-event cost). Screen views are
 * dropped because Amplitude models screens and sessions via its own autocapture.
 *
 * The allowlist is opt-in: a newly added `trackXxx` event does NOT reach Amplitude
 * until its name is added to the allowlist. See
 * docs/analytics/AMPLITUDE_EVENT_SCHEMA.md.
 */
internal class AllowlistFilteringBackend(
    private val delegate: AnalyticsBackend,
    private val allowlist: Set<String>,
) : AnalyticsBackend {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        if (name in allowlist) {
            delegate.logEvent(name, params)
        }
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        delegate.setUserProperty(name, value)
    }
}
