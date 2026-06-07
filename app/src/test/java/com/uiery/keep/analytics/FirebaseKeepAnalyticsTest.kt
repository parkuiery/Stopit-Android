package com.uiery.keep.analytics

import com.uiery.keep.analytics.acquisition.AcquisitionAttributionParser
import com.uiery.keep.analytics.acquisition.InstallReferrerLookupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirebaseKeepAnalyticsTest {
    private val backend = FakeAnalyticsBackend()
    private val analytics = FirebaseKeepAnalytics(backend)

    @Test
    fun onboardingEventsUseCanonicalSchema() {
        analytics.trackOnboardingStepView(OnboardingStepName.INTRO)
        analytics.trackOnboardingStepComplete(OnboardingStepName.SELECT_APP)
        analytics.trackFirstOpen()

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.ONBOARDING_STEP_VIEW,
                params = mapOf(KeepAnalyticsParam.STEP_NAME to OnboardingStepName.INTRO),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.ONBOARDING_STEP_COMPLETE,
                params = mapOf(KeepAnalyticsParam.STEP_NAME to OnboardingStepName.SELECT_APP),
            ),
            backend.loggedEvents[1],
        )
        assertEquals(
            2,
            backend.loggedEvents.size,
        )
    }

    @Test
    fun permissionOutcomeIncludesStableParams() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.DENIED,
            stepName = OnboardingStepName.NOTIFICATION,
        )

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.PERMISSION_OUTCOME,
                params = mapOf(
                    KeepAnalyticsParam.PERMISSION_NAME to AnalyticsPermissionName.NOTIFICATIONS,
                    KeepAnalyticsParam.OUTCOME to AnalyticsOutcome.DENIED,
                    KeepAnalyticsParam.STEP_NAME to OnboardingStepName.NOTIFICATION,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun lockAndEmergencyEventsIncludeCanonicalParams() {
        analytics.trackFirstLockConfigured(
            source = AnalyticsSource.ONBOARDING,
            selectedAppCount = 3,
        )
        analytics.trackLockSessionStart(
            source = AnalyticsSource.HOME_TIMER,
            isRoutine = false,
        )
        analytics.trackLockSessionEnd(
            source = AnalyticsSource.HOME_TIMER,
            endReason = AnalyticsEndReason.TIMER_ELAPSED,
            isRoutine = false,
        )
        analytics.trackEmergencyUnlockUsed(
            source = AnalyticsSource.BLOCK_SCREEN,
            unlockCountRemaining = 1,
        )

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.FIRST_LOCK_CONFIGURED,
                params = mapOf(
                    KeepAnalyticsParam.SOURCE to AnalyticsSource.ONBOARDING,
                    KeepAnalyticsParam.SELECTED_APP_COUNT to 3,
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.LOCK_SESSION_START,
                params = mapOf(
                    KeepAnalyticsParam.SOURCE to AnalyticsSource.HOME_TIMER,
                    KeepAnalyticsParam.IS_ROUTINE to false,
                ),
            ),
            backend.loggedEvents[1],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.LOCK_SESSION_END,
                params = mapOf(
                    KeepAnalyticsParam.SOURCE to AnalyticsSource.HOME_TIMER,
                    KeepAnalyticsParam.END_REASON to AnalyticsEndReason.TIMER_ELAPSED,
                    KeepAnalyticsParam.IS_ROUTINE to false,
                ),
            ),
            backend.loggedEvents[2],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_USED,
                params = mapOf(
                    KeepAnalyticsParam.SOURCE to AnalyticsSource.BLOCK_SCREEN,
                    KeepAnalyticsParam.UNLOCK_COUNT_REMAINING to 1,
                ),
            ),
            backend.loggedEvents[3],
        )
    }

    @Test
    fun dpcBaselineEventsUseRequiredNamesAndParams() {
        analytics.trackAppSelectionCompleted(
            selectedAppCount = 2,
            isOnboarding = true,
        )
        analytics.trackKeepModeToggled(isEnabled = true)
        analytics.trackLockScheduled(
            scheduleType = AnalyticsScheduleType.TIMER,
            scheduledDurationMinutes = 45,
        )
        analytics.trackAppBlockIntercepted(
            blockSource = AnalyticsBlockSource.TIMED_LOCK,
            blockedAppPackage = "com.example.blocked",
        )
        analytics.trackAppBlockIntercepted(
            blockSource = AnalyticsBlockSource.ROUTINE,
            blockedAppPackage = "com.example.routine",
            routineId = "42",
        )
        analytics.trackAppBlockIntercepted(
            blockSource = AnalyticsBlockSource.GOAL_LOCK,
            blockedAppPackage = "com.example.goal",
            goalLockId = "77",
        )
        analytics.trackEmergencyUnlockCompleted(
            reason = "work",
            durationMinutes = 15,
            remainingUnlocks = 1,
        )

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.APP_SELECTION_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.SELECTED_APP_COUNT to 2,
                    KeepAnalyticsParam.IS_ONBOARDING to true,
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.KEEP_MODE_TOGGLED,
                params = mapOf(KeepAnalyticsParam.IS_ENABLED to true),
            ),
            backend.loggedEvents[1],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.LOCK_SCHEDULED,
                params = mapOf(
                    KeepAnalyticsParam.SCHEDULE_TYPE to AnalyticsScheduleType.TIMER,
                    KeepAnalyticsParam.SCHEDULED_DURATION_MINUTES to 45L,
                ),
            ),
            backend.loggedEvents[2],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                params = mapOf(
                    KeepAnalyticsParam.BLOCK_SOURCE to AnalyticsBlockSource.TIMED_LOCK,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to BlockedAppCategoryBucket.UNKNOWN,
                ),
            ),
            backend.loggedEvents[3],
        )
        assertFalse(backend.loggedEvents[3].params.containsKey("blocked_app_package"))
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                params = mapOf(
                    KeepAnalyticsParam.BLOCK_SOURCE to AnalyticsBlockSource.ROUTINE,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to BlockedAppCategoryBucket.UNKNOWN,
                    KeepAnalyticsParam.ROUTINE_ID to "42",
                ),
            ),
            backend.loggedEvents[4],
        )
        assertFalse(backend.loggedEvents[4].params.containsKey("blocked_app_package"))
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                params = mapOf(
                    KeepAnalyticsParam.BLOCK_SOURCE to AnalyticsBlockSource.GOAL_LOCK,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to BlockedAppCategoryBucket.UNKNOWN,
                    KeepAnalyticsParam.GOAL_LOCK_ID to "77",
                ),
            ),
            backend.loggedEvents[5],
        )
        assertFalse(backend.loggedEvents[5].params.containsKey("blocked_app_package"))
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.REASON to "work",
                    KeepAnalyticsParam.DURATION_MINUTES to 15,
                    KeepAnalyticsParam.REMAINING_UNLOCKS to 1,
                ),
            ),
            backend.loggedEvents[6],
        )
    }

    @Test
    fun coreActionAndDeviceRegistrationEventsUseRequiredContract() {
        analytics.trackFirstCoreActionCompleted(
            elapsedSinceFirstOpenSeconds = 120,
            blockingMode = AnalyticsBlockSource.MANUAL_KEEP,
            blockedAppPackage = "com.example.blocked",
            routineId = null,
        )
        analytics.trackCoreActionCompleted(
            elapsedSinceFirstOpenSeconds = 180,
            blockingMode = AnalyticsBlockSource.ROUTINE,
            blockedAppPackage = "com.example.routine",
            routineId = "routine-1",
        )
        analytics.trackFcmTokenCaptured()
        analytics.trackDeviceRegistrationAttempted()
        analytics.trackDeviceRegistrationSkipped("backend_removed")

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS to 120L,
                    KeepAnalyticsParam.BLOCKING_MODE to AnalyticsBlockSource.MANUAL_KEEP,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to BlockedAppCategoryBucket.UNKNOWN,
                ),
            ),
            backend.loggedEvents[0],
        )
        assertFalse(backend.loggedEvents[0].params.containsKey("blocked_app_package"))
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.CORE_ACTION_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS to 180L,
                    KeepAnalyticsParam.BLOCKING_MODE to AnalyticsBlockSource.ROUTINE,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to BlockedAppCategoryBucket.UNKNOWN,
                    KeepAnalyticsParam.ROUTINE_ID to "routine-1",
                ),
            ),
            backend.loggedEvents[1],
        )
        assertFalse(backend.loggedEvents[1].params.containsKey("blocked_app_package"))
        assertEquals(LoggedEvent(KeepAnalyticsEvent.FCM_TOKEN_CAPTURED, emptyMap()), backend.loggedEvents[2])
        assertEquals(LoggedEvent(KeepAnalyticsEvent.DEVICE_REGISTRATION_ATTEMPTED, emptyMap()), backend.loggedEvents[3])
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.DEVICE_REGISTRATION_SKIPPED,
                mapOf(KeepAnalyticsParam.REASON to "backend_removed"),
            ),
            backend.loggedEvents[4],
        )
    }

    @Test
    fun routineTemplateShareEventsUseSafeBucketedParamsOnly() {
        analytics.trackRoutineTemplateShareTapped(
            templateCategory = RoutineTemplateCategoryName.STUDY,
            repeatDaysBucket = RoutineTemplateRepeatDaysBucketName.WEEKDAY,
            timeWindowBucket = RoutineTemplateTimeWindowBucketName.EVENING,
            routineNameIncluded = false,
        )
        analytics.trackRoutineTemplateShareSheetOpened(
            templateCategory = RoutineTemplateCategoryName.STUDY,
            repeatDaysBucket = RoutineTemplateRepeatDaysBucketName.WEEKDAY,
            timeWindowBucket = RoutineTemplateTimeWindowBucketName.EVENING,
            routineNameIncluded = false,
        )
        analytics.trackRoutineTemplateShareFailed(
            templateCategory = RoutineTemplateCategoryName.CUSTOM,
            reason = RoutineTemplateShareFailureReason.INVALID_TEMPLATE,
        )

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_TAPPED,
                params = mapOf(
                    KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.STUDY,
                    KeepAnalyticsParam.REPEAT_DAYS_BUCKET to RoutineTemplateRepeatDaysBucketName.WEEKDAY,
                    KeepAnalyticsParam.TIME_WINDOW_BUCKET to RoutineTemplateTimeWindowBucketName.EVENING,
                    KeepAnalyticsParam.ROUTINE_NAME_INCLUDED to false,
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_SHEET_OPENED,
                params = mapOf(
                    KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.STUDY,
                    KeepAnalyticsParam.REPEAT_DAYS_BUCKET to RoutineTemplateRepeatDaysBucketName.WEEKDAY,
                    KeepAnalyticsParam.TIME_WINDOW_BUCKET to RoutineTemplateTimeWindowBucketName.EVENING,
                    KeepAnalyticsParam.ROUTINE_NAME_INCLUDED to false,
                ),
            ),
            backend.loggedEvents[1],
        )
        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.ROUTINE_TEMPLATE_SHARE_FAILED,
                params = mapOf(
                    KeepAnalyticsParam.TEMPLATE_CATEGORY to RoutineTemplateCategoryName.CUSTOM,
                    KeepAnalyticsParam.REASON to RoutineTemplateShareFailureReason.INVALID_TEMPLATE,
                ),
            ),
            backend.loggedEvents[2],
        )
    }

    @Test
    fun deviceRegistrationRuntimeEventsOnlyExposeCurrentBackendRemovedContract() {
        assertEquals(
            setOf(
                KeepAnalyticsEvent.FCM_TOKEN_CAPTURED,
                KeepAnalyticsEvent.DEVICE_REGISTRATION_ATTEMPTED,
                KeepAnalyticsEvent.DEVICE_REGISTRATION_SKIPPED,
            ),
            KeepAnalyticsEvent.ACTIVE_DEVICE_REGISTRATION_EVENTS,
        )
    }

    @Test
    fun reviewPromptEventsUseCanonicalSchema() {
        analytics.reviewPromptEligible()
        analytics.reviewPromptShown()
        analytics.reviewPromptSkipped("WithinCooldown")
        analytics.reviewPromptFailed("no_play_services")

        assertEquals(LoggedEvent(KeepAnalyticsEvent.REVIEW_PROMPT_ELIGIBLE, emptyMap()), backend.loggedEvents[0])
        assertEquals(LoggedEvent(KeepAnalyticsEvent.REVIEW_PROMPT_SHOWN, emptyMap()), backend.loggedEvents[1])
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.REVIEW_PROMPT_SKIPPED,
                mapOf(KeepAnalyticsParam.REASON to "WithinCooldown"),
            ),
            backend.loggedEvents[2],
        )
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.REVIEW_PROMPT_FAILED,
                mapOf(KeepAnalyticsParam.ERROR to "no_play_services"),
            ),
            backend.loggedEvents[3],
        )
    }

    @Test
    fun focusSummaryShareEventsUsePrivacySafeBuckets() {
        analytics.trackFocusSummaryShareTapped(
            periodType = "week",
            sessionCountBucket = "2_3",
            durationMinutesBucket = "120_239",
        )
        analytics.trackFocusSummaryShareSheetOpened(
            periodType = "week",
            sessionCountBucket = "2_3",
            durationMinutesBucket = "120_239",
        )
        analytics.trackFocusSummaryShareFailed(
            periodType = "week",
            reason = "activity_not_found",
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_TAPPED,
                mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to "week",
                    KeepAnalyticsParam.SESSION_COUNT_BUCKET to "2_3",
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to "120_239",
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_SHEET_OPENED,
                mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to "week",
                    KeepAnalyticsParam.SESSION_COUNT_BUCKET to "2_3",
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to "120_239",
                ),
            ),
            backend.loggedEvents[1],
        )
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_FAILED,
                mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to "week",
                    KeepAnalyticsParam.REASON to "activity_not_found",
                ),
            ),
            backend.loggedEvents[2],
        )
    }

    @Test
    fun lockHistoryPerformanceEventsUsePrivacySafeBuckets() {
        analytics.trackLockHistoryPerformanceSummaryViewed(
            periodType = "month",
            reportState = "has_history",
            sessionCountBucket = "4_6",
            durationMinutesBucket = "120_239",
        )
        analytics.trackLockHistoryTopAppsViewed(
            periodType = "month",
            topAppsCountBucket = "2_3",
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.LOCK_HISTORY_PERFORMANCE_SUMMARY_VIEWED,
                mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to "month",
                    KeepAnalyticsParam.REPORT_STATE to "has_history",
                    KeepAnalyticsParam.SESSION_COUNT_BUCKET to "4_6",
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to "120_239",
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.LOCK_HISTORY_TOP_APPS_VIEWED,
                mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to "month",
                    KeepAnalyticsParam.TOP_APPS_COUNT_BUCKET to "2_3",
                ),
            ),
            backend.loggedEvents[1],
        )
    }

    @Test
    fun monetizationInterestEventsUseExperimentContextParams() {
        analytics.trackMonetizationInterestShown(
            interestSurface = AnalyticsMonetizationInterestSurface.MENU,
            interestContext = AnalyticsMonetizationInterestContext.POST_SPLIT_ADMOB_AUDIT,
        )
        analytics.trackMonetizationInterestClicked(
            interestSurface = AnalyticsMonetizationInterestSurface.MENU,
            interestContext = AnalyticsMonetizationInterestContext.POST_SPLIT_ADMOB_AUDIT,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.MONETIZATION_INTEREST_SHOWN,
                mapOf(
                    KeepAnalyticsParam.INTEREST_SURFACE to AnalyticsMonetizationInterestSurface.MENU,
                    KeepAnalyticsParam.INTEREST_CONTEXT to AnalyticsMonetizationInterestContext.POST_SPLIT_ADMOB_AUDIT,
                ),
            ),
            backend.loggedEvents[0],
        )
        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.MONETIZATION_INTEREST_CLICKED,
                mapOf(
                    KeepAnalyticsParam.INTEREST_SURFACE to AnalyticsMonetizationInterestSurface.MENU,
                    KeepAnalyticsParam.INTEREST_CONTEXT to AnalyticsMonetizationInterestContext.POST_SPLIT_ADMOB_AUDIT,
                ),
            ),
            backend.loggedEvents[1],
        )
    }

    @Test
    fun parentModeStartedUsesSafeBucketedParamsOnly() {
        analytics.trackParentModeStarted(
            durationMinutesBucket = AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY,
            allowedAppCountBucket = AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.PARENT_MODE_STARTED,
                mapOf(
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY,
                    KeepAnalyticsParam.ALLOWED_APP_COUNT_BUCKET to AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun parentModeCompletedDoesNotSendRawTimestampsOrPackages() {
        analytics.trackParentModeCompleted(
            durationMinutesBucket = AnalyticsParentModeDurationBucket.TWENTY_ONE_TO_THIRTY,
            endReason = AnalyticsParentModeEndReason.TIME_EXPIRED,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.PARENT_MODE_COMPLETED,
                mapOf(
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to AnalyticsParentModeDurationBucket.TWENTY_ONE_TO_THIRTY,
                    KeepAnalyticsParam.END_REASON to AnalyticsParentModeEndReason.TIME_EXPIRED,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun goalLockCreatedUsesSafeBucketedParamsOnly() {
        analytics.trackGoalLockCreated(
            durationSelectionType = AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
            lockMode = AnalyticsGoalLockMode.ALL_DAY,
            selectedAppCountBucket = AnalyticsSelectedAppCountBucket.FOUR_TO_SIX,
            goalNameType = AnalyticsGoalLockNameType.PRESET_EXAM,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.GOAL_LOCK_CREATED,
                mapOf(
                    KeepAnalyticsParam.DURATION_SELECTION_TYPE to AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
                    KeepAnalyticsParam.LOCK_MODE to AnalyticsGoalLockMode.ALL_DAY,
                    KeepAnalyticsParam.SELECTED_APP_COUNT_BUCKET to AnalyticsSelectedAppCountBucket.FOUR_TO_SIX,
                    KeepAnalyticsParam.GOAL_NAME_TYPE to AnalyticsGoalLockNameType.PRESET_EXAM,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun goalLockEndedEarlyUsesSafeBucketedParamsOnly() {
        analytics.trackGoalLockEndedEarly(
            lockMode = AnalyticsGoalLockMode.SCHEDULED,
            elapsedDaysBucket = AnalyticsGoalLockElapsedDaysBucket.SEVEN_TO_FOURTEEN,
            reason = AnalyticsGoalLockEndedEarlyReason.USER_CONFIRMED,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.GOAL_LOCK_ENDED_EARLY,
                mapOf(
                    KeepAnalyticsParam.LOCK_MODE to AnalyticsGoalLockMode.SCHEDULED,
                    KeepAnalyticsParam.ELAPSED_DAYS_BUCKET to AnalyticsGoalLockElapsedDaysBucket.SEVEN_TO_FOURTEEN,
                    KeepAnalyticsParam.REASON to AnalyticsGoalLockEndedEarlyReason.USER_CONFIRMED,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun goalLockCompletedUsesSafeDurationBucketOnly() {
        analytics.trackGoalLockCompleted(
            lockMode = AnalyticsGoalLockMode.ALL_DAY,
            durationDaysBucket = AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY,
        )

        assertEquals(
            LoggedEvent(
                KeepAnalyticsEvent.GOAL_LOCK_COMPLETED,
                mapOf(
                    KeepAnalyticsParam.LOCK_MODE to AnalyticsGoalLockMode.ALL_DAY,
                    KeepAnalyticsParam.DURATION_DAYS_BUCKET to AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY,
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun installReferrerAttributionEventUsesPrivacySafeParams() {
        val attribution = AcquisitionAttributionParser.parse(
            rawReferrer = "utm_source=discord&utm_medium=social&utm_campaign=aso_baseline&email=private@example.com",
            lookupStatus = InstallReferrerLookupStatus.SUCCESS,
            latencyMillis = 450,
        )

        analytics.trackInstallReferrerAttributionChecked(attribution)

        assertEquals(
            LoggedEvent(
                name = KeepAnalyticsEvent.INSTALL_REFERRER_ATTRIBUTION_CHECKED,
                params = mapOf(
                    KeepAnalyticsParam.REFERRER_STATUS to "success",
                    KeepAnalyticsParam.UTM_SOURCE_TYPE to "discord",
                    KeepAnalyticsParam.UTM_MEDIUM_TYPE to "social",
                    KeepAnalyticsParam.CAMPAIGN_BUCKET to "aso_baseline",
                    KeepAnalyticsParam.LINK_SURFACE to "discord",
                    KeepAnalyticsParam.LOOKUP_LATENCY_BUCKET to "0_499ms",
                ),
            ),
            backend.loggedEvents.single(),
        )
    }

    @Test
    fun analyticsConstantValuesStayQueryableInGa4() {
        assertEquals("fcm_token_captured", KeepAnalyticsEvent.FCM_TOKEN_CAPTURED)
        assertEquals("focus_summary_share_tapped", KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_TAPPED)
        assertEquals("lock_history_performance_summary_viewed", KeepAnalyticsEvent.LOCK_HISTORY_PERFORMANCE_SUMMARY_VIEWED)
        assertEquals("lock_history_top_apps_viewed", KeepAnalyticsEvent.LOCK_HISTORY_TOP_APPS_VIEWED)
        assertEquals("session_count_bucket", KeepAnalyticsParam.SESSION_COUNT_BUCKET)
        assertEquals("report_state", KeepAnalyticsParam.REPORT_STATE)
        assertEquals("top_apps_count_bucket", KeepAnalyticsParam.TOP_APPS_COUNT_BUCKET)
        assertEquals("missing_fcm_token", AnalyticsDeviceRegistrationSkipReason.MISSING_FCM_TOKEN)
        assertEquals("SplashScreen", KeepAnalyticsScreen.SPLASH)
        assertEquals("HomeScreen", KeepAnalyticsScreen.HOME)
        assertEquals("MenuScreen", KeepAnalyticsScreen.MENU)
        assertEquals("LockHistoryScreen", KeepAnalyticsScreen.LOCK_HISTORY)
        assertEquals("BlockedAppsScreen", KeepAnalyticsScreen.BLOCKED_APPS)
        assertEquals("RoutineScreen", KeepAnalyticsScreen.ROUTINE)
        assertEquals("EmergencyUnlockSettingsScreen", KeepAnalyticsScreen.EMERGENCY_UNLOCK_SETTINGS)
        assertEquals("BlockScreen", KeepAnalyticsScreen.BLOCK)
        assertEquals("LockScreen", KeepAnalyticsScreen.LOCK)
        assertEquals("goal_lock_completed", KeepAnalyticsEvent.GOAL_LOCK_COMPLETED)
        assertEquals("install_referrer_attribution_checked", KeepAnalyticsEvent.INSTALL_REFERRER_ATTRIBUTION_CHECKED)
        assertEquals("duration_days_bucket", KeepAnalyticsParam.DURATION_DAYS_BUCKET)
        assertEquals("referrer_status", KeepAnalyticsParam.REFERRER_STATUS)
        assertEquals("campaign_bucket", KeepAnalyticsParam.CAMPAIGN_BUCKET)
        assertEquals("15_30", AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY)
    }
}

private data class LoggedEvent(
    val name: String,
    val params: Map<String, Any?>,
)

private class FakeAnalyticsBackend : AnalyticsBackend {
    val loggedEvents = mutableListOf<LoggedEvent>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        loggedEvents += LoggedEvent(name = name, params = params)
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit
}
