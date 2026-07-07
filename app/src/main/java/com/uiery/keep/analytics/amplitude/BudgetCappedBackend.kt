package com.uiery.keep.analytics.amplitude

import com.uiery.keep.analytics.AnalyticsBackend

/**
 * Drops events once the per-device monthly Amplitude budget is exhausted, hard-capping
 * ingestion so the free tier is never exceeded (and billing is never triggered).
 *
 * User properties are forwarded without consuming budget: they are low-volume and only
 * sent alongside allowlisted events. Screen views are already dropped upstream by
 * [com.uiery.keep.analytics.AllowlistFilteringBackend].
 */
internal class BudgetCappedBackend(
    private val delegate: AnalyticsBackend,
    private val budget: AmplitudeEventBudget,
) : AnalyticsBackend {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        if (budget.tryConsume()) {
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
