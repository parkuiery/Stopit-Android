package com.uiery.keep.analytics

import com.uiery.keep.analytics.acquisition.AcquisitionAttribution
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.domain.firstpromise.AnalysisLatencyBucket
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.domain.firstpromise.FirstPromisePracticeOutcome
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PromiseEditField
import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType

interface KeepAnalytics {
    fun logEvent(
        name: String,
        params: Map<String, Any?> = emptyMap(),
    )

    fun log(event: AnalyticsEvent) {
        logEvent(name = event.name, params = event.params)
    }

    fun logScreenView(screenName: String)

    fun setUserProperty(
        name: String,
        value: String,
    )

    fun trackFirstOpen()

    fun trackOnboardingStepView(stepName: String)

    fun trackOnboardingStepComplete(stepName: String)

    fun trackOnboardingExperimentExposed(
        variant: OnboardingVariant,
        assignmentVersion: OnboardingAssignmentVersion,
    ) = Unit

    fun trackUsageAnalysisCompleted(
        dataQuality: UsageDataQuality,
        patternType: UsagePatternType,
        coverageDaysBucket: UsageCoverageBucket,
        latencyBucket: AnalysisLatencyBucket,
    ) = Unit

    fun trackPromiseRecommendationShown(
        goalType: FirstPromiseGoal,
        patternType: UsagePatternType,
        source: FirstPromiseSource,
    ) = Unit

    fun trackPromiseRecommendationEdited(fieldName: PromiseEditField) = Unit

    fun trackFirstPromiseCreated(
        goalType: FirstPromiseGoal,
        source: FirstPromiseSource,
        scheduleState: FirstPromiseScheduleState,
    ) = Unit

    fun trackFirstPromisePracticeOutcome(outcome: FirstPromisePracticeOutcome) = Unit

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
        promiseOrigin: FirstPromiseOrigin? = null,
    ) = Unit

    fun trackEmergencyUnlockCompleted(
        reason: String,
        durationMinutes: Int,
        remainingUnlocks: Int,
    ) = Unit

    fun trackEmergencyUnlockSettingsChanged(
        settingName: String,
        valueBucket: String,
        refillMode: String,
        durationCountBucket: String,
        source: String,
    ) = Unit

    fun trackEmergencyUnlockManualResetRequested(
        remainingUnlocksBucket: String,
        source: String,
        resetResult: String? = null,
    ) = Unit

    fun trackEmergencyUnlockStepViewed(
        stepName: String,
        reasonRequiredEnabled: Boolean,
        source: String,
    ) = Unit

    fun trackEmergencyUnlockValidationBlocked(
        stepName: String,
        validationReason: String,
        reasonRequiredEnabled: Boolean,
        source: String,
    ) = Unit

    fun trackEmergencyUnlockCancelled(
        stepName: String,
        reasonRequiredEnabled: Boolean,
        source: String,
        cancelSource: String = AnalyticsEmergencyUnlockCancelSource.UNKNOWN,
    ) = Unit

    /**
     * 시스템 VPN 동의창의 결과. 거부율이 곧 "웹 차단을 켰다고 믿지만 켜지지 않은" 사용자
     * 비율이라, 이 값 없이는 기능이 도달했는지 자체를 알 수 없다.
     */
    fun trackWebsiteBlockingConsentResult(
        granted: Boolean,
        source: String,
    ) = Unit

    /**
     * 다른 VPN 이 슬롯을 쥐고 있을 때 사용자가 무엇을 골랐는지. [displacedOtherVpn] 이 false 면
     * 잠금은 돌지만 웹사이트는 열려 있다.
     */
    fun trackWebsiteBlockingVpnConflictResolved(
        displacedOtherVpn: Boolean,
        source: String,
    ) = Unit

    /**
     * 웹 차단 필터가 실제로 어떤 상태인지. 잠금이 도는 도중에도 바뀔 수 있어서
     * (동의 회수, 다른 VPN 이 슬롯을 가져감, 업스트림 DNS 무응답) 시작 시점 이벤트만으로는
     * 잡히지 않는다. [status] 는 [com.uiery.keep.websiteblocking.WebsiteBlockingStatus] 이름이다.
     *
     * surface 를 싣지 않는다. 이 전환은 화면과 무관하게 일어나고
     * [com.uiery.keep.websiteblocking.WebsiteBlockingStatusReporter] 가 프로세스 단위로 관찰한다.
     */
    fun trackWebsiteBlockingStatusChanged(status: String) = Unit

    /**
     * 루틴이 웹 차단을 세우거나 내린 회차. 이 경로는 화면 없이 돌기 때문에 사용자도 우리도
     * 조용히 실패를 놓칠 수 있는 유일한 자리다. 특히 [AnalyticsWebsiteBlockingRoutineOutcome.CONSENT_MISSING]
     * 은 창은 열렸는데 동의가 없어 웹이 뚫려 있는 상태이고, 수신자에서는 동의창을 띄울 수도 없다.
     *
     * [trigger] 는 네 계기 중 무엇이 이 세션을 세웠는지다. 알람이 거의 못 세우고 부팅·편집이
     * 계속 구제하고 있다면 이중화의 성공이 아니라 알람 경로가 고장 났다는 신호다.
     */
    fun trackWebsiteBlockingRoutineSession(
        outcome: String,
        trigger: String,
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

    fun trackSupportContactStarted(surface: String) = Unit

    fun trackSupportContactFallbackUsed(
        surface: String,
        fallbackType: String,
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

    fun trackGoalLockCreateStarted(entrySurface: String) = Unit

    fun trackGoalLockCreated(
        durationSelectionType: String,
        lockMode: String,
        selectedAppCountBucket: String,
        goalNameType: String,
    ) = Unit

    fun trackPomodoroSessionStarted(
        preset: String,
        entrySurface: String,
        selectedAppCountBucket: String,
        focusMinutesBucket: String,
        cycleCountBucket: String,
    ) = Unit

    fun trackPomodoroFocusCompleted(
        preset: String,
        cycleIndexBucket: String,
    ) = Unit

    fun trackPomodoroBreakStarted(
        preset: String,
        breakType: String,
    ) = Unit

    fun trackPomodoroSessionEnded(
        preset: String,
        endReason: String,
        completedFocusCountBucket: String,
        elapsedMinutesBucket: String,
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

    fun trackGoalLockUpdated(
        lockMode: String,
        changedField: String,
    ) = Unit

    fun trackRepeatBlockRoutineSuggestionShown(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = Unit

    fun trackRepeatBlockRoutineSuggestionClicked(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = Unit

    fun trackRepeatBlockRoutineSuggestionDismissed(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = Unit

    fun trackRepeatBlockRoutineSuggestionApplied(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) = Unit

    fun trackRoutineSaved(payload: RoutineSavedAnalyticsPayload) = Unit

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

    fun trackInstallReferrerAttributionChecked(attribution: AcquisitionAttribution) = Unit

    fun trackRoutineCreationCtaShown(
        surface: String,
        activationStage: String,
        hasRoutine: Boolean,
        ctaVariant: String? = null,
    ) = Unit

    fun trackRoutineCreationCtaClicked(
        surface: String,
        activationStage: String,
        hasRoutine: Boolean,
        ctaVariant: String? = null,
    ) = Unit

    fun trackRoutineCreationCtaDismissed(
        surface: String,
        activationStage: String,
        hasRoutine: Boolean,
        ctaVariant: String? = null,
    ) = Unit
}

object KeepAnalyticsEvent {
    // Firebase auto-emits the reserved `first_open`; this custom, non-reserved event
    // carries the first-open signal to non-Firebase backends (Amplitude).
    const val APP_FIRST_OPEN = "app_first_open"
    const val ONBOARDING_STEP_VIEW = "onboarding_step_view"
    const val ONBOARDING_STEP_COMPLETE = "onboarding_step_complete"
    const val ONBOARDING_EXPERIMENT_EXPOSED = "onboarding_experiment_exposed"
    const val USAGE_ANALYSIS_COMPLETED = "usage_analysis_completed"
    const val PROMISE_RECOMMENDATION_SHOWN = "promise_recommendation_shown"
    const val PROMISE_RECOMMENDATION_EDITED = "promise_recommendation_edited"
    const val FIRST_PROMISE_CREATED = "first_promise_created"
    const val FIRST_PROMISE_PRACTICE_OUTCOME = "first_promise_practice_outcome"
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
    const val EMERGENCY_UNLOCK_SETTINGS_CHANGED = "emergency_unlock_settings_changed"
    const val EMERGENCY_UNLOCK_MANUAL_RESET_REQUESTED = "emergency_unlock_manual_reset_requested"
    const val EMERGENCY_UNLOCK_STEP_VIEWED = "emergency_unlock_step_viewed"
    const val EMERGENCY_UNLOCK_VALIDATION_BLOCKED = "emergency_unlock_validation_blocked"
    const val EMERGENCY_UNLOCK_CANCELLED = "emergency_unlock_cancelled"

    /*
     * 웹 차단은 사용자가 승인해야만 서고, 다른 VPN 앱 하나에 밀려 조용히 내려갈 수 있다.
     * "잠금은 켜졌다"와 "웹사이트가 실제로 막혔다"의 차이를 출시 후에 판별할 수 있는 신호는
     * 이 셋뿐이다. 도메인은 어느 이벤트에도 싣지 않는다.
     * docs/WEBSITE_BLOCKING_VPN_SPIKE.md 의 Privacy Rules 를 따른다.
     */
    const val WEBSITE_BLOCKING_CONSENT_RESULT = "website_blocking_consent_result"
    const val WEBSITE_BLOCKING_VPN_CONFLICT_RESOLVED = "website_blocking_vpn_conflict_resolved"
    const val WEBSITE_BLOCKING_STATUS_CHANGED = "website_blocking_status_changed"
    const val WEBSITE_BLOCKING_ROUTINE_SESSION = "website_blocking_routine_session"
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
    const val SUPPORT_CONTACT_STARTED = "support_contact_started"
    const val SUPPORT_CONTACT_FALLBACK_USED = "support_contact_fallback_used"
    const val GOAL_LOCK_CREATE_STARTED = "goal_lock_create_started"
    const val POMODORO_SESSION_STARTED = "pomodoro_session_started"
    const val POMODORO_FOCUS_COMPLETED = "pomodoro_focus_completed"
    const val POMODORO_BREAK_STARTED = "pomodoro_break_started"
    const val POMODORO_SESSION_ENDED = "pomodoro_session_ended"
    const val GOAL_LOCK_CREATED = "goal_lock_created"
    const val GOAL_LOCK_ENDED_EARLY = "goal_lock_ended_early"
    const val GOAL_LOCK_COMPLETED = "goal_lock_completed"
    const val GOAL_LOCK_UPDATED = "goal_lock_updated"
    const val PARENT_MODE_DURATION_SELECTED = "parent_mode_duration_selected"
    const val PARENT_MODE_ALLOWED_APPS_SELECTED = "parent_mode_allowed_apps_selected"
    const val PARENT_MODE_STARTED = "parent_mode_started"
    const val PARENT_MODE_COMPLETED = "parent_mode_completed"
    const val PARENT_MODE_UNLOCKED_BY_PIN = "parent_mode_unlocked_by_pin"
    const val PARENT_MODE_EXTENDED = "parent_mode_extended"
    const val PARENT_MODE_BLOCK_INTERCEPTED = "parent_mode_block_intercepted"
    const val PARENT_MODE_CANCELLED = "parent_mode_cancelled"
    const val INSTALL_REFERRER_ATTRIBUTION_CHECKED = "install_referrer_attribution_checked"
    const val ROUTINE_CREATION_CTA_SHOWN = "routine_creation_cta_shown"
    const val ROUTINE_CREATION_CTA_CLICKED = "routine_creation_cta_clicked"
    const val ROUTINE_CREATION_CTA_DISMISSED = "routine_creation_cta_dismissed"
}

object KeepAnalyticsParam {
    const val STEP_NAME = "step_name"
    const val VARIANT = "variant"
    const val ASSIGNMENT_VERSION = "assignment_version"
    const val DATA_QUALITY = "data_quality"
    const val PATTERN_TYPE = "pattern_type"
    const val COVERAGE_DAYS_BUCKET = "coverage_days_bucket"
    const val LATENCY_BUCKET = "latency_bucket"
    const val GOAL_TYPE = "goal_type"
    const val FIELD_NAME = "field_name"
    const val SCHEDULE_STATE = "schedule_state"
    const val PERMISSION_NAME = "permission_name"
    const val OUTCOME = "outcome"
    const val SOURCE = "source"
    const val IS_GRANTED = "is_granted"
    const val DISPLACED_OTHER_VPN = "displaced_other_vpn"
    const val WEBSITE_BLOCKING_STATUS = "website_blocking_status"
    const val WEBSITE_BLOCKING_TRIGGER = "website_blocking_trigger"
    const val IS_ROUTINE = "is_routine"
    const val END_REASON = "end_reason"
    const val UNLOCK_COUNT_REMAINING = "unlock_count_remaining"
    const val SELECTED_APP_COUNT = "selected_app_count"
    const val IS_ONBOARDING = "is_onboarding"
    const val IS_ENABLED = "is_enabled"
    const val SCHEDULE_TYPE = "schedule_type"
    const val SCHEDULED_DURATION_MINUTES = "scheduled_duration_minutes"
    const val BLOCK_SOURCE = "block_source"
    @Deprecated("Use BLOCKED_APP_CATEGORY_BUCKET for external analytics payloads.")
    const val BLOCKED_APP_PACKAGE = "blocked_app_package"
    const val BLOCKED_APP_CATEGORY_BUCKET = "blocked_app_category_bucket"
    const val PROMISE_ORIGIN = "promise_origin"
    const val REASON = "reason"
    const val DURATION_MINUTES = "duration_minutes"
    const val REMAINING_UNLOCKS = "remaining_unlocks"
    const val ELAPSED_SINCE_FIRST_OPEN_SECONDS = "elapsed_since_first_open_seconds"
    const val ELAPSED_SINCE_FIRST_OPEN_BUCKET = "elapsed_since_first_open_bucket"
    const val BLOCKING_MODE = "blocking_mode"
    // Do not export routine row IDs to GA4; use block_source/routines_count/bucketed params instead.
    const val ROUTINE_ID = "routine_id"
    // Do not export goal-lock row IDs to GA4; use block_source/lock_mode/duration buckets instead.
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
    const val DURATION_SELECTION_TYPE = "duration_selection_type"
    const val ENTRY_SURFACE = "entry_surface"
    const val PRESET = "preset"
    const val BREAK_TYPE = "break_type"
    const val CYCLE_INDEX_BUCKET = "cycle_index_bucket"
    const val FOCUS_MINUTES_BUCKET = "focus_minutes_bucket"
    const val CYCLE_COUNT_BUCKET = "cycle_count_bucket"
    const val COMPLETED_FOCUS_COUNT_BUCKET = "completed_focus_count_bucket"
    const val ELAPSED_MINUTES_BUCKET = "elapsed_minutes_bucket"
    const val LOCK_MODE = "lock_mode"
    const val SELECTED_APP_COUNT_BUCKET = "selected_app_count_bucket"
    const val GOAL_NAME_TYPE = "goal_name_type"
    const val ELAPSED_DAYS_BUCKET = "elapsed_days_bucket"
    const val DURATION_DAYS_BUCKET = "duration_days_bucket"
    const val CHANGED_FIELD = "changed_field"
    const val SURFACE = "surface"
    const val ALLOWED_APP_COUNT_BUCKET = "allowed_app_count_bucket"
    const val PIN_RESULT = "pin_result"
    const val EXTENSION_MINUTES_BUCKET = "extension_minutes_bucket"
    const val BLOCK_CONTEXT = "block_context"
    const val REFERRER_STATUS = "referrer_status"
    const val UTM_SOURCE_TYPE = "utm_source_type"
    const val UTM_MEDIUM_TYPE = "utm_medium_type"
    const val CAMPAIGN_BUCKET = "campaign_bucket"
    const val LINK_SURFACE = "link_surface"
    const val LOOKUP_LATENCY_BUCKET = "lookup_latency_bucket"
    const val ACTIVATION_STAGE = "activation_stage"
    const val HAS_ROUTINE = "has_routine"
    const val CTA_VARIANT = "cta_variant"
    const val FALLBACK_TYPE = "fallback_type"
    const val SETTING_NAME = "setting_name"
    const val VALUE_BUCKET = "value_bucket"
    const val REFILL_MODE = "refill_mode"
    const val DURATION_COUNT_BUCKET = "duration_count_bucket"
    const val REMAINING_UNLOCKS_BUCKET = "remaining_unlocks_bucket"
    const val RESET_RESULT = "reset_result"
    const val VALIDATION_REASON = "validation_reason"
    const val REASON_REQUIRED_ENABLED = "reason_required_enabled"
    const val CANCEL_SOURCE = "cancel_source"
}

object OnboardingStepName {
    const val INTRO = "intro"
    const val PERMISSION = "permission"
    const val NOTIFICATION = "notification"
    const val SELECT_APP = "select_app"
    const val GOAL_SELECT = "goal_select"
    const val USAGE_ACCESS = "usage_access"
    const val PROMISE_PROPOSAL = "promise_proposal"
    const val PROMISE_RESULT = "promise_result"
}

object KeepAnalyticsScreen {
    const val SPLASH = "SplashScreen"
    const val ONBOARDING_INTRO = "OnboardingIntroScreen"
    const val ONBOARDING_PERMISSION = "OnboardingPermissionScreen"
    const val ONBOARDING_NOTIFICATION = "OnboardingNotificationScreen"
    const val ONBOARDING_SELECT_APP = "OnboardingSelectAppScreen"
    const val ONBOARDING_GOAL_SELECT = "OnboardingGoalSelectScreen"
    const val ONBOARDING_USAGE_ACCESS = "OnboardingUsageAccessScreen"
    const val ONBOARDING_USAGE_ANALYSIS = "OnboardingUsageAnalysisScreen"
    const val ONBOARDING_PROMISE_PROPOSAL = "OnboardingPromiseProposalScreen"
    const val ONBOARDING_PROMISE_RESULT = "OnboardingPromiseResultScreen"
    const val HOME = "HomeScreen"
    const val MENU = "MenuScreen"
    const val LOCK_HISTORY = "LockHistoryScreen"
    const val BLOCKED_APPS = "BlockedAppsScreen"
    const val ROUTINE = "RoutineScreen"
    const val EMERGENCY_UNLOCK_SETTINGS = "EmergencyUnlockSettingsScreen"
    const val DEV_TOOL = "DevToolScreen"
    const val GOAL_LOCK_CREATION = "GoalLockCreationScreen"
    const val GOAL_LOCK_DETAIL = "GoalLockDetailScreen"
    const val GOAL_LOCK_EDIT = "GoalLockEditScreen"
    const val PARENT_MODE_SETUP = "ParentModeSetupScreen"
    const val BLOCK = "BlockScreen"
    const val LOCK = "LockScreen"

    val CANONICAL_SCREEN_NAMES = setOf(
        SPLASH,
        ONBOARDING_INTRO,
        ONBOARDING_PERMISSION,
        ONBOARDING_NOTIFICATION,
        ONBOARDING_SELECT_APP,
        ONBOARDING_GOAL_SELECT,
        ONBOARDING_USAGE_ACCESS,
        ONBOARDING_USAGE_ANALYSIS,
        HOME,
        MENU,
        LOCK_HISTORY,
        BLOCKED_APPS,
        ROUTINE,
        EMERGENCY_UNLOCK_SETTINGS,
        DEV_TOOL,
        GOAL_LOCK_CREATION,
        GOAL_LOCK_DETAIL,
        GOAL_LOCK_EDIT,
        PARENT_MODE_SETUP,
        BLOCK,
        LOCK,
    )
}

/**
 * 루틴 웹 차단 판정이 실제로 한 일. 아무것도 하지 않은 회차(`DoNothing`)는 값이 없다 —
 * 알람과 부팅은 창 밖에서도 돌기 때문에, 그것까지 세면 판정 횟수가 세션 신호를 덮는다.
 */
object AnalyticsWebsiteBlockingRoutineOutcome {
    const val STARTED = "started"
    const val STOPPED = "stopped"

    /** 창은 열렸는데 VPN 동의가 없어 웹이 뚫려 있다. 수신자에서는 동의창을 띄울 수 없다. */
    const val CONSENT_MISSING = "consent_missing"
}

object AnalyticsSource {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HOME_TIMER = "home_timer"
    const val HOME_POMODORO = "home_pomodoro"
    const val HOME_KEEP_SWITCH = "home_keep_switch"
    const val ROUTINE = "routine"
    const val ROUTINE_ALARM = "routine_alarm"
    const val LOCK_SCREEN = "lock_screen"
    const val BLOCK_SCREEN = "block_screen"
    const val MENU = "menu"
}

object AnalyticsEmergencyUnlockSettingName {
    const val ENABLED = "enabled"
    const val DAILY_LIMIT = "daily_limit"
    const val DURATION_OPTIONS = "duration_options"
    const val REASON_REQUIRED = "reason_required"
    const val REFILL_MODE = "refill_mode"
    const val COUNTDOWN = "countdown"
    const val COUNTDOWN_SECONDS = "countdown_seconds"
}

object AnalyticsEmergencyUnlockSettingsValueBucket {
    const val ON = "on"
    const val OFF = "off"
    const val ONE = "1"
    const val TWO = "2"
    const val THREE = "3"
    const val FOUR_PLUS = "4_plus"
    const val NONE = "none"
    const val SHORT_ONLY = "short_only"
    const val MIXED = "mixed"
    const val LONG_INCLUDED = "long_included"
    const val DAILY = "daily"
    const val MANUAL = "manual"
    const val SECONDS_10 = "10s"
    const val SECONDS_30 = "30s"
    const val SECONDS_60 = "60s"
}

object AnalyticsEmergencyUnlockRefillMode {
    const val DAILY = "daily"
    const val MANUAL = "manual"
    const val NOT_APPLICABLE = "not_applicable"
}

object AnalyticsEmergencyUnlockDurationCountBucket {
    const val ZERO = "0"
    const val ONE = "1"
    const val TWO_TO_THREE = "2_3"
    const val FOUR_PLUS = "4_plus"
    const val NOT_APPLICABLE = "not_applicable"
}

object AnalyticsEmergencyUnlockRemainingUnlocksBucket {
    const val ZERO = "0"
    const val ONE = "1"
    const val TWO = "2"
    const val THREE_PLUS = "3_plus"
    const val UNKNOWN = "unknown"
}

object AnalyticsEmergencyUnlockManualResetResult {
    const val REQUESTED = "requested"
    const val COMPLETED = "completed"
    const val UNAVAILABLE = "unavailable"
}

object AnalyticsEmergencyUnlockStepName {
    const val REASON = "reason"
    const val APPS = "app_selection"
    const val DURATION = "duration"
    const val COUNTDOWN = "countdown"
}

object AnalyticsEmergencyUnlockValidationReason {
    const val MISSING_REASON = "missing_reason"
    const val MISSING_CUSTOM_REASON = "missing_custom_reason"
    const val MISSING_APP_SELECTION = "missing_app_selection"
}

object AnalyticsEmergencyUnlockCancelSource {
    const val CANCEL_BUTTON = "cancel_button"
    const val SHEET_DISMISS = "sheet_dismiss"
    const val BACK = "back"
    const val OUTSIDE_TAP = "outside_tap"
    const val SYSTEM = "system"
    const val UNKNOWN = "unknown"
}

object AnalyticsPermissionName {
    const val ACCESSIBILITY = "accessibility"
    const val NOTIFICATIONS = "notifications"
    const val USAGE_ACCESS = "usage_access"
}

object AnalyticsOutcome {
    const val GRANTED = "granted"
    const val DENIED = "denied"
    const val SETTINGS_OPENED = "settings_opened"
    const val SKIPPED = "skipped"
    const val UNKNOWN = "unknown"
}

object AnalyticsEndReason {
    const val TIMER_ELAPSED = "timer_elapsed"
    const val USER_TOGGLE_OFF = "user_toggle_off"
}

object AnalyticsScheduleType {
    const val TIMER = "timer"
    const val COUNTDOWN = "countdown"
    const val ROUTINE = "routine"

    // 집중 세션은 사이클 전체가 하나의 예약이다. 예약 길이는 집중 하나가 아니라 총 길이다.
    const val POMODORO = "pomodoro"
}

object AnalyticsBlockSource {
    const val MANUAL_KEEP = "manual_keep"
    const val TIMED_LOCK = "timed_lock"
    const val ROUTINE = "routine"
    const val GOAL_LOCK = "goal_lock"
    const val PARENT_MODE = "parent_mode"

    // 뽀모도로 세션 중 차단. `timed_lock` 에 합산하지 않는다 — 합치면 집중 세션의 성과를
    // 기존 타이머 잠금과 분리할 수 없다.
    const val POMODORO = "pomodoro"
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

object AnalyticsGoalLockDurationSelectionType {
    const val PRESET_DAYS = "preset_days"
    const val CUSTOM_DAYS = "custom_days"
    const val END_DATE = "end_date"
}

object AnalyticsGoalLockEntrySurface {
    const val HOME = "home"
    const val ROUTINE = "routine"
    const val MENU = "menu"
    const val GOAL_LOCK_DETAIL = "goal_lock_detail"
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

object AnalyticsGoalLockChangedField {
    const val DURATION = "duration"
    const val APPS = "apps"
    const val SCHEDULE = "schedule"
    const val NAME = "name"
    const val LOCK_MODE = "lock_mode"
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

object AnalyticsRoutineCreationCtaSurface {
    const val HOME_SECONDARY = "home_secondary"
}

object AnalyticsRoutineCreationCtaActivationStage {
    const val POST_FIRST_CORE_ACTION = "post_first_core_action"
}

object AnalyticsRoutineCreationCtaVariant {
    const val SOFT_DEFAULT = "soft_default"
}
