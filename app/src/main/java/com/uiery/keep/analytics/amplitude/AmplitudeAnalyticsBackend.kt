package com.uiery.keep.analytics.amplitude

import com.amplitude.android.Amplitude
import com.amplitude.android.events.Identify
import com.uiery.keep.analytics.AnalyticsBackend

/**
 * [AnalyticsBackend] backed by the Amplitude Android SDK.
 *
 * Only receives allowlisted events because it is always wrapped by
 * [com.uiery.keep.analytics.AllowlistFilteringBackend]. Null-valued params are
 * dropped so Amplitude never stores empty properties. Screen views are ignored;
 * Amplitude captures sessions via its own autocapture configuration.
 */
internal class AmplitudeAnalyticsBackend(
    private val amplitude: Amplitude,
) : AnalyticsBackend {
    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        amplitude.track(name, params.filterValues { it != null })
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        amplitude.identify(Identify().set(name, value))
    }
}
