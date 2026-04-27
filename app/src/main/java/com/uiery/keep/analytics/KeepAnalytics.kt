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

    fun trackAppSelectionCompleted(
        selectedAppCount: Int,
        isOnboarding: Boolean,
    ) = Unit

    fun trackKeepModeToggled(isEnabled: Boolean) = Unit

    fun trackLockScheduled(
        scheduleType: String,
        scheduledDurationMinutes: Long,
    ) = Unit

    fun trackAppBlockIntercepted(
        blockSource: String,
        blockedAppPackage: String,
    ) = Unit

    fun trackEmergencyUnlockCompleted(
        reason: String,
        durationMinutes: Int,
        remainingUnlocks: Int,
    ) = Unit

    fun trackFirstCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String? = null,
    ) = Unit

    fun trackCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String? = null,
    ) = Unit

    fun trackFcmTokenCaptured() = Unit

    fun trackDeviceRegistrationAttempted() = Unit

    fun trackDeviceRegistrationSucceeded() = Unit

    fun trackDeviceRegistrationFailed(reason: String) = Unit

    fun trackDeviceRegistrationSkipped(reason: String) = Unit
}

object KeepAnalyticsEvent {
    const val ONBOARDING_STEP_VIEW = "onboarding_step_view"
    const val ONBOARDING_STEP_COMPLETE = "onboarding_step_complete"
    const val PERMISSION_OUTCOME = "permission_outcome"
    const val FIRST_LOCK_CONFIGURED = "first_lock_configured"
    const val LOCK_SESSION_START = "lock_session_start"
    const val LOCK_SESSION_END = "lock_session_end"
    const val EMERGENCY_UNLOCK_USED = "emergency_unlock_used"
    const val APP_SELECTION_COMPLETED = "app_selection_completed"
    const val KEEP_MODE_TOGGLED = "keep_mode_toggled"
    const val LOCK_SCHEDULED = "lock_scheduled"
    const val APP_BLOCK_INTERCEPTED = "app_block_intercepted"
    const val EMERGENCY_UNLOCK_COMPLETED = "emergency_unlock_completed"
    const val FIRST_CORE_ACTION_COMPLETED = "first_core_action_completed"
    const val CORE_ACTION_COMPLETED = "core_action_completed"
    const val FCM_TOKEN_CAPTURED = "fcm_token_captured"
    const val DEVICE_REGISTRATION_ATTEMPTED = "device_registration_attempted"
    const val DEVICE_REGISTRATION_SUCCEEDED = "device_registration_succeeded"
    const val DEVICE_REGISTRATION_FAILED = "device_registration_failed"
    const val DEVICE_REGISTRATION_SKIPPED = "device_registration_skipped"
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
    const val IS_ONBOARDING = "is_onboarding"
    const val IS_ENABLED = "is_enabled"
    const val SCHEDULE_TYPE = "schedule_type"
    const val SCHEDULED_DURATION_MINUTES = "scheduled_duration_minutes"
    const val BLOCK_SOURCE = "block_source"
    const val BLOCKED_APP_PACKAGE = "blocked_app_package"
    const val REASON = "reason"
    const val DURATION_MINUTES = "duration_minutes"
    const val REMAINING_UNLOCKS = "remaining_unlocks"
    const val ELAPSED_SINCE_FIRST_OPEN_SECONDS = "elapsed_since_first_open_seconds"
    const val BLOCKING_MODE = "blocking_mode"
    const val ROUTINE_ID = "routine_id"
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

object AnalyticsScheduleType {
    const val TIMER = "timer"
    const val COUNTDOWN = "countdown"
    const val ROUTINE = "routine"
}

object AnalyticsBlockSource {
    const val MANUAL_KEEP = "manual_keep"
    const val TIMED_LOCK = "timed_lock"
    const val ROUTINE = "routine"
}

object AnalyticsDeviceRegistrationSkipReason {
    const val BACKEND_REMOVED = "backend_removed"
    const val MISSING_FCM_TOKEN = "missing_fcm_token"
}
