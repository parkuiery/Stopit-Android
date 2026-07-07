package com.uiery.keep.analytics.amplitude

import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.routine.RoutineAnalyticsEvent
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drift guard: every name in the Amplitude allowlist must correspond to a real event
 * constant, and the allowlist must stay intentionally small. This fails the build on a
 * typo or a renamed/removed constant instead of silently dropping (or leaking) traffic.
 */
class AmplitudeEventAllowlistTest {

    private val knownEventNames: Set<String> =
        stringConstantsOf(KeepAnalyticsEvent) + stringConstantsOf(RoutineAnalyticsEvent)

    @Test
    fun everyAllowlistedEventMapsToARealEventConstant() {
        val unknown = AmplitudeEventAllowlist.EVENTS - knownEventNames

        assertTrue(
            "Amplitude allowlist has names with no matching event constant: $unknown",
            unknown.isEmpty(),
        )
    }

    @Test
    fun allowlistStaysNonEmptyAndBoundedForFreeTierBudget() {
        assertTrue("Allowlist must not be empty", AmplitudeEventAllowlist.EVENTS.isNotEmpty())
        // Guardrail: a large allowlist risks blowing the free-tier event budget. Raising
        // this ceiling is a deliberate decision that should be paired with budget math in
        // docs/analytics/AMPLITUDE_EVENT_SCHEMA.md.
        assertTrue(
            "Allowlist grew beyond the budgeted ceiling (${AmplitudeEventAllowlist.EVENTS.size})",
            AmplitudeEventAllowlist.EVENTS.size <= 30,
        )
    }

    private fun stringConstantsOf(owner: Any): Set<String> =
        owner.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                field.get(owner) as? String
            }
            .toSet()
}
