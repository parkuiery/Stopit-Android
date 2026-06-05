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
        routineId: String? = null,
        goalLockId: String? = null,
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
        goalLockId: String? = null,
    ) = Unit

    fun trackCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String? = null,
        goalLockId: String? = null,
    ) = Unit

    fun trackFcmTokenCaptured() = Unit

    fun trackDeviceRegistrationAttempted() = Unit

    fun trackDeviceRegistrationSkipped(reason: String) = Unit

    fun reviewPromptEligible() = Unit

    fun reviewPromptShown() = Unit

    fun reviewPromptSkipped(reason: String) = Unit

    fun reviewPromptFailed(error: String) = Unit

    fun trackFocusSummaryShareTapped(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    fun trackFocusSummaryShareSheetOpened(
        periodType: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    fun trackFocusSummaryShareFailed(
        periodType: String,
        reason: String,
    ) = Unit

    fun trackLockHistoryPerformanceSummaryViewed(
        periodType: String,
        reportState: String,
        sessionCountBucket: String,
        durationMinutesBucket: String,
    ) = Unit

    fun trackLockHistoryTopAppsViewed(
        periodType: String,
        topAppsCountBucket: String,
    ) = Unit

    fun trackMonetizationInterestShown(
        interestSurface: String,
        interestContext: String,
        interestVariant: String? = null,
        purchaseAvailable: Boolean? = null,
    ) = Unit

    fun trackMonetizationInterestClicked(
        interestSurface: String,
        interestContext: String,
        interestVariant: String? = null,
        purchaseAvailable: Boolean? = null,
    ) = Unit

    fun trackRoutineTemplateShareTapped(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) = Unit

    fun trackRoutineTemplateShareSheetOpened(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) = Unit

    fun trackRoutineTemplateShareFailed(
        templateCategory: String,
        reason: String,
    ) = Unit

    fun trackGoalLockCreated(
        durationSelectionType: String,
        lockMode: String,
        selectedAppCountBucket: String,
        goalNameType: String,
    ) = Unit

    fun trackGoalLockEndedEarly(
        lockMode: String,
        elapsedDaysBucket: String,
        reason: String,
    ) = Unit

    fun trackGoalLockCompleted(
        lockMode: String,
        durationDaysBucket: String,
    ) = Unit

    fun trackParentModeDurationSelected(durationMinutesBucket: String) = Unit

    fun trackParentModeAllowedAppsSelected(allowedAppCountBucket: String) = Unit

    fun trackParentModeStarted(
        durationMinutesBucket: String,
        allowedAppCountBucket: String,
    ) = Unit

    fun trackParentModeCompleted(
        durationMinutesBucket: String,
        endReason: String,
    ) = Unit

    fun trackParentModeUnlockedByPin(
        pinResult: String,
        endReason: String,
    ) = Unit

    fun trackParentModeExtended(extensionMinutesBucket: String) = Unit

    fun trackParentModeBlockIntercepted(blockContext: String) = Unit

    fun trackParentModeCancelled(endReason: String) = Unit
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
    const val DEVICE_REGISTRATION_SKIPPED = "device_registration_skipped"
    val ACTIVE_DEVICE_REGISTRATION_EVENTS = setOf(
        FCM_TOKEN_CAPTURED,
        DEVICE_REGISTRATION_ATTEMPTED,
        DEVICE_REGISTRATION_SKIPPED,
    )
    const val REVIEW_PROMPT_ELIGIBLE = "review_prompt_eligible"
    const val REVIEW_PROMPT_SHOWN = "review_prompt_shown"
    const val REVIEW_PROMPT_SKIPPED = "review_prompt_skipped"
    const val REVIEW_PROMPT_FAILED = "review_prompt_failed"
    const val FOCUS_SUMMARY_SHARE_TAPPED = "focus_summary_share_tapped"
    const val FOCUS_SUMMARY_SHARE_SHEET_OPENED = "focus_summary_share_sheet_opened"
    const val FOCUS_SUMMARY_SHARE_FAILED = "focus_summary_share_failed"
    const val LOCK_HISTORY_PERFORMANCE_SUMMARY_VIEWED = "lock_history_performance_summary_viewed"
    const val LOCK_HISTORY_TOP_APPS_VIEWED = "lock_history_top_apps_viewed"
    const val MONETIZATION_INTEREST_SHOWN = "monetization_interest_shown"
    const val MONETIZATION_INTEREST_CLICKED = "monetization_interest_clicked"
    const val ROUTINE_TEMPLATE_SHARE_TAPPED = "routine_template_share_tapped"
    const val ROUTINE_TEMPLATE_SHARE_SHEET_OPENED = "routine_template_share_sheet_opened"
    const val ROUTINE_TEMPLATE_SHARE_FAILED = "routine_template_share_failed"
    const val GOAL_LOCK_CREATED = "goal_lock_created"
    const val GOAL_LOCK_ENDED_EARLY = "goal_lock_ended_early"
    const val GOAL_LOCK_COMPLETED = "goal_lock_completed"
    const val PARENT_MODE_DURATION_SELECTED = "parent_mode_duration_selected"
    const val PARENT_MODE_ALLOWED_APPS_SELECTED = "parent_mode_allowed_apps_selected"
    const val PARENT_MODE_STARTED = "parent_mode_started"
    const val PARENT_MODE_COMPLETED = "parent_mode_completed"
    const val PARENT_MODE_UNLOCKED_BY_PIN = "parent_mode_unlocked_by_pin"
    const val PARENT_MODE_EXTENDED = "parent_mode_extended"
    const val PARENT_MODE_BLOCK_INTERCEPTED = "parent_mode_block_intercepted"
    const val PARENT_MODE_CANCELLED = "parent_mode_cancelled"
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
    const val GOAL_LOCK_ID = "goal_lock_id"
    const val ERROR = "error"
    const val PERIOD_TYPE = "period_type"
    const val REPORT_STATE = "report_state"
    const val SESSION_COUNT_BUCKET = "session_count_bucket"
    const val DURATION_MINUTES_BUCKET = "duration_minutes_bucket"
    const val TOP_APPS_COUNT_BUCKET = "top_apps_count_bucket"
    const val INTEREST_SURFACE = "interest_surface"
    const val INTEREST_CONTEXT = "interest_context"
    const val INTEREST_VARIANT = "interest_variant"
    const val PURCHASE_AVAILABLE = "purchase_available"
    const val TEMPLATE_CATEGORY = "template_category"
    const val REPEAT_DAYS_BUCKET = "repeat_days_bucket"
    const val TIME_WINDOW_BUCKET = "time_window_bucket"
    const val ROUTINE_NAME_INCLUDED = "routine_name_included"
    const val DURATION_SELECTION_TYPE = "duration_selection_type"
    const val LOCK_MODE = "lock_mode"
    const val SELECTED_APP_COUNT_BUCKET = "selected_app_count_bucket"
    const val GOAL_NAME_TYPE = "goal_name_type"
    const val ELAPSED_DAYS_BUCKET = "elapsed_days_bucket"
    const val DURATION_DAYS_BUCKET = "duration_days_bucket"
    const val ALLOWED_APP_COUNT_BUCKET = "allowed_app_count_bucket"
    const val PIN_RESULT = "pin_result"
    const val EXTENSION_MINUTES_BUCKET = "extension_minutes_bucket"
    const val BLOCK_CONTEXT = "block_context"
}

object OnboardingStepName {
    const val INTRO = "intro"
    const val PERMISSION = "permission"
    const val NOTIFICATION = "notification"
    const val SELECT_APP = "select_app"
}

object KeepAnalyticsScreen {
    const val SPLASH = "SplashScreen"
    const val ONBOARDING_INTRO = "OnboardingIntroScreen"
    const val ONBOARDING_PERMISSION = "OnboardingPermissionScreen"
    const val ONBOARDING_NOTIFICATION = "OnboardingNotificationScreen"
    const val ONBOARDING_SELECT_APP = "OnboardingSelectAppScreen"
    const val HOME = "HomeScreen"
    const val MENU = "MenuScreen"
    const val LOCK_HISTORY = "LockHistoryScreen"
    const val BLOCKED_APPS = "BlockedAppsScreen"
    const val ROUTINE = "RoutineScreen"
    const val EMERGENCY_UNLOCK_SETTINGS = "EmergencyUnlockSettingsScreen"
    const val DEV_TOOL = "DevToolScreen"
    const val BLOCK = "BlockScreen"
    const val LOCK = "LockScreen"
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
    const val GOAL_LOCK = "goal_lock"
}

object AnalyticsDeviceRegistrationSkipReason {
    const val BACKEND_REMOVED = "backend_removed"
    const val MISSING_FCM_TOKEN = "missing_fcm_token"
}

object AnalyticsMonetizationInterestSurface {
    const val MENU = "menu"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

object AnalyticsMonetizationInterestContext {
    const val POST_SPLIT_ADMOB_AUDIT = "post_split_admob_audit"
    const val MENU_SETTINGS = "menu_settings"
    const val HOME_SECONDARY = "home_secondary"
    const val AD_MANAGEMENT = "ad_management"
}

object RoutineTemplateCategoryName {
    const val STUDY = "study"
    const val WORK = "work"
    const val NIGHT_FOCUS = "night_focus"
    const val CUSTOM = "custom"
}

object RoutineTemplateRepeatDaysBucketName {
    const val WEEKDAY = "weekday"
    const val WEEKEND = "weekend"
    const val DAILY = "daily"
    const val CUSTOM_DAYS = "custom_days"
    const val NONE = "none"
}

object RoutineTemplateTimeWindowBucketName {
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"
    const val OVERNIGHT = "overnight"
    const val CUSTOM_WINDOW = "custom_window"
}

object RoutineTemplateShareFailureReason {
    const val ACTIVITY_NOT_FOUND = "activity_not_found"
    const val INVALID_TEMPLATE = "invalid_template"
}

object AnalyticsGoalLockDurationSelectionType {
    const val PRESET_DAYS = "preset_days"
    const val CUSTOM_DAYS = "custom_days"
    const val END_DATE = "end_date"
}

object AnalyticsGoalLockMode {
    const val ALL_DAY = "all_day"
    const val SCHEDULED = "scheduled"
}

object AnalyticsSelectedAppCountBucket {
    const val ONE = "1"
    const val TWO_TO_THREE = "2_3"
    const val FOUR_TO_SIX = "4_6"
    const val SEVEN_PLUS = "7_plus"
}

object AnalyticsGoalLockNameType {
    const val PRESET_EXAM = "preset_exam"
    const val PRESET_SNS = "preset_sns"
    const val PRESET_GAME = "preset_game"
    const val PRESET_SLEEP = "preset_sleep"
    const val CUSTOM = "custom"
}

object AnalyticsGoalLockElapsedDaysBucket {
    const val ZERO = "0"
    const val ONE_TO_TWO = "1_2"
    const val THREE_TO_SIX = "3_6"
    const val SEVEN_TO_FOURTEEN = "7_14"
    const val FIFTEEN_PLUS = "15_plus"
}

object AnalyticsGoalLockDurationDaysBucket {
    const val ONE_TO_SIX = "1_6"
    const val SEVEN = "7"
    const val EIGHT_TO_FOURTEEN = "8_14"
    const val FIFTEEN_TO_THIRTY = "15_30"
    const val THIRTY_ONE_PLUS = "31_plus"
}

object AnalyticsGoalLockEndedEarlyReason {
    const val USER_CONFIRMED = "user_confirmed"
    const val VALIDATION_RESET = "validation_reset"
    const val UNKNOWN = "unknown"
}

object AnalyticsParentModeDurationBucket {
    const val ONE_TO_NINE = "1_9"
    const val TEN = "10"
    const val ELEVEN_TO_TWENTY = "11_20"
    const val TWENTY_ONE_TO_THIRTY = "21_30"
    const val THIRTY_ONE_TO_SIXTY = "31_60"
    const val SIXTY_ONE_PLUS = "61_plus"
}

object AnalyticsParentModeExtensionMinutesBucket {
    const val ONE_TO_NINE = "1_9"
    const val TEN = "10"
    const val ELEVEN_TO_TWENTY = "11_20"
    const val TWENTY_ONE_TO_THIRTY = "21_30"
    const val THIRTY_ONE_PLUS = "31_plus"
}

object AnalyticsParentModeAllowedAppCountBucket {
    const val ONE = "1"
    const val TWO_TO_THREE = "2_3"
    const val FOUR_TO_SIX = "4_6"
    const val SEVEN_PLUS = "7_plus"
}

object AnalyticsParentModePinResult {
    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val NOT_CONFIGURED = "not_configured"
}

object AnalyticsParentModeEndReason {
    const val TIME_EXPIRED = "time_expired"
    const val PIN_UNLOCKED = "pin_unlocked"
    const val CANCELLED_BEFORE_START = "cancelled_before_start"
    const val CANCELLED_BY_PARENT = "cancelled_by_parent"
    const val SYSTEM_INTERRUPTED = "system_interrupted"
    const val UNKNOWN = "unknown"
}

object AnalyticsParentModeBlockContext {
    const val DISALLOWED_APP = "disallowed_app"
    const val SETTINGS_SURFACE = "settings_surface"
    const val RECENT_APPS = "recent_apps"
    const val NOTIFICATION_SURFACE = "notification_surface"
    const val UNKNOWN = "unknown"
}
