package com.uiery.keep.analytics

interface KeepAnalytics {
    fun logEvent(
        name: String,
        params: Map<String, Any?> = emptyMap(),
    )

    fun logScreenView(screenName: String)

    fun setUserProperty(
        name: String,
        value: String,
    )

    fun trackFirstOpen()

    fun trackOnboardingStepView(stepName: String)

    fun trackOnboardingStepComplete(stepName: String)

    fun trackPermissionOutcome(
        permissionName: String,
        outcome: String,
        stepName: String? = null,
    )

    fun trackFirstLockConfigured(
        source: String,
        selectedAppCount: Int? = null,
    )

    fun trackLockSessionStart(
        source: String,
        isRoutine: Boolean? = null,
    )

    fun trackLockSessionEnd(
        source: String,
        endReason: String,
        isRoutine: Boolean? = null,
    )

    fun trackEmergencyUnlockUsed(
        source: String,
        unlockCountRemaining: Int? = null,
    )
}

object KeepAnalyticsEvent {
    const val FIRST_OPEN = "first_open"
    const val ONBOARDING_STEP_VIEW = "onboarding_step_view"
    const val ONBOARDING_STEP_COMPLETE = "onboarding_step_complete"
    const val PERMISSION_OUTCOME = "permission_outcome"
    const val FIRST_LOCK_CONFIGURED = "first_lock_configured"
    const val LOCK_SESSION_START = "lock_session_start"
    const val LOCK_SESSION_END = "lock_session_end"
    const val EMERGENCY_UNLOCK_USED = "emergency_unlock_used"
}

object KeepAnalyticsParam {
    const val STEP_NAME = "step_name"
    const val PERMISSION_NAME = "permission_name"
    const val OUTCOME = "outcome"
    const val SOURCE = "source"
    const val IS_ROUTINE = "is_routine"
    const val END_REASON = "end_reason"
    const val UNLOCK_COUNT_REMAINING = "unlock_count_remaining"
    const val SELECTED_APP_COUNT = "selected_app_count"
}

object OnboardingStepName {
    const val INTRO = "intro"
    const val PERMISSION = "permission"
    const val NOTIFICATION = "notification"
    const val SELECT_APP = "select_app"
}

object AnalyticsSource {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HOME_TIMER = "home_timer"
    const val HOME_KEEP_SWITCH = "home_keep_switch"
    const val ROUTINE = "routine"
    const val ROUTINE_ALARM = "routine_alarm"
    const val LOCK_SCREEN = "lock_screen"
    const val BLOCK_SCREEN = "block_screen"
}

object AnalyticsPermissionName {
    const val ACCESSIBILITY = "accessibility"
    const val NOTIFICATIONS = "notifications"
}

object AnalyticsOutcome {
    const val GRANTED = "granted"
    const val DENIED = "denied"
    const val SETTINGS_OPENED = "settings_opened"
}

object AnalyticsEndReason {
    const val TIMER_ELAPSED = "timer_elapsed"
    const val USER_TOGGLE_OFF = "user_toggle_off"
}
