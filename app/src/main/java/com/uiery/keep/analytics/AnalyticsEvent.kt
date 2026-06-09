package com.uiery.keep.analytics

/**
 * Canonical analytics dispatch unit.
 *
 * Feature-specific analytics catalogs should build this value and keep event
 * names/parameter keys out of the central Firebase adapter.
 */
data class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any?> = emptyMap(),
)
