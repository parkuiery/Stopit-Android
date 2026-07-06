package com.uiery.keep.analytics.amplitude

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.uiery.keep.BuildConfig
import com.uiery.keep.analytics.AllowlistFilteringBackend
import com.uiery.keep.analytics.AnalyticsBackend
import com.uiery.keep.analytics.NoOpAnalyticsBackend

/**
 * Builds the Amplitude-backed [AnalyticsBackend], or a no-op when Amplitude cannot or
 * should not run.
 *
 * Returns [NoOpAnalyticsBackend] when Amplitude is disabled for the build flavor
 * (`AMPLITUDE_ENABLED` is false for dev, so dev never sends and needs no dedicated
 * Amplitude project), when the API key is absent (keyless builds), or when the SDK fails
 * to initialize. Never throws — analytics must never crash the host app.
 *
 * Every Amplitude event flows through two gates before upload:
 * 1. [AllowlistFilteringBackend] — only high-signal allowlisted events are forwarded.
 * 2. [BudgetCappedBackend] — a hard per-device monthly cap so ingestion can never exceed
 *    the free tier. Autocapture is fully disabled ([emptySet]) precisely so the SDK cannot
 *    emit ungated events that would bypass these gates and risk billing.
 */
internal object AmplitudeAnalyticsBackendFactory {
    fun create(
        context: Context,
        apiKey: String,
        enabled: Boolean = BuildConfig.AMPLITUDE_ENABLED,
        maxMonthlyEvents: Int = BuildConfig.AMPLITUDE_MONTHLY_EVENT_CAP,
    ): AnalyticsBackend {
        if (!enabled || apiKey.isBlank()) return NoOpAnalyticsBackend

        return try {
            val amplitude = Amplitude(
                Configuration(
                    apiKey = apiKey,
                    context = context.applicationContext,
                    // Autocapture fully OFF: sessions, screen views, deep links, element
                    // interactions and app-lifecycle events would all be emitted internally by
                    // the SDK, bypassing our allowlist and budget gates. Disabling them is what
                    // makes the per-device monthly cap a true hard ceiling on ingestion.
                    autocapture = emptySet(),
                ),
            )
            val budget = SharedPreferencesAmplitudeEventBudget(
                context = context.applicationContext,
                maxMonthlyEvents = maxMonthlyEvents,
            )
            AllowlistFilteringBackend(
                delegate = BudgetCappedBackend(AmplitudeAnalyticsBackend(amplitude), budget),
                allowlist = AmplitudeEventAllowlist.EVENTS,
            )
        } catch (_: Throwable) {
            NoOpAnalyticsBackend
        }
    }
}
