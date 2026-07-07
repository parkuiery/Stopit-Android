package com.uiery.keep.analytics.amplitude

import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.routine.RoutineAnalyticsEvent

/**
 * The subset of the app's event catalog forwarded to Amplitude.
 *
 * Curated to stay inside the Amplitude free tier (2M events/mo; ~200 events/user/mo
 * average). Only activation, retention, funnel and monetization-intent events are
 * included; high-frequency events (app_block_intercepted, core_action_completed,
 * granular step/validation events, screen views, infra events) are intentionally
 * excluded and remain Firebase-only.
 *
 * Opt-in by design: a newly added event does NOT reach Amplitude until its name is
 * added here. [AmplitudeEventAllowlistTest] guards every entry against a real event
 * constant so a typo or a removed constant fails the build instead of silently
 * dropping traffic. See docs/analytics/AMPLITUDE_EVENT_SCHEMA.md for the rationale
 * and budget math behind each inclusion.
 */
internal object AmplitudeEventAllowlist {
    val EVENTS: Set<String> = setOf(
        // First-open marker (Firebase auto-emits first_open; Amplitude needs an explicit event).
        KeepAnalyticsEvent.APP_FIRST_OPEN,
        // Activation funnel
        KeepAnalyticsEvent.ONBOARDING_STEP_COMPLETE,
        KeepAnalyticsEvent.PERMISSION_OUTCOME,
        KeepAnalyticsEvent.APP_SELECTION_COMPLETED,
        KeepAnalyticsEvent.FIRST_LOCK_CONFIGURED,
        KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED,
        // Core engagement / retention
        KeepAnalyticsEvent.KEEP_MODE_TOGGLED,
        KeepAnalyticsEvent.LOCK_SESSION_START,
        KeepAnalyticsEvent.LOCK_SESSION_END,
        KeepAnalyticsEvent.LOCK_SCHEDULED,
        KeepAnalyticsEvent.EMERGENCY_UNLOCK_USED,
        KeepAnalyticsEvent.EMERGENCY_UNLOCK_COMPLETED,
        // Goal lock lifecycle
        KeepAnalyticsEvent.GOAL_LOCK_CREATED,
        KeepAnalyticsEvent.GOAL_LOCK_COMPLETED,
        KeepAnalyticsEvent.GOAL_LOCK_ENDED_EARLY,
        // Routine
        RoutineAnalyticsEvent.ROUTINE_SAVED,
        KeepAnalyticsEvent.ROUTINE_CREATION_CTA_CLICKED,
        // Parent mode
        KeepAnalyticsEvent.PARENT_MODE_STARTED,
        KeepAnalyticsEvent.PARENT_MODE_COMPLETED,
        // Retention surfaces / monetization intent
        KeepAnalyticsEvent.LOCK_HISTORY_PERFORMANCE_SUMMARY_VIEWED,
        KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_TAPPED,
        KeepAnalyticsEvent.REVIEW_PROMPT_SHOWN,
        KeepAnalyticsEvent.MONETIZATION_INTEREST_CLICKED,
    )
}
