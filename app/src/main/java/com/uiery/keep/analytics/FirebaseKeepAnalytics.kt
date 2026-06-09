package com.uiery.keep.analytics

import com.uiery.keep.analytics.acquisition.AcquisitionAttribution
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RoutineAnalyticsEvents
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseKeepAnalytics
    @Inject
    constructor(
        private val backend: AnalyticsBackend,
    ) : KeepAnalytics {
        override fun logEvent(
            name: String,
            params: Map<String, Any?>,
        ) {
            backend.logEvent(name = name, params = params)
        }

        override fun logScreenView(screenName: String) {
            backend.logScreenView(screenName = screenName)
        }

        override fun setUserProperty(
            name: String,
            value: String,
        ) {
            backend.setUserProperty(name = name, value = value)
        }

        override fun trackFirstOpen() {
            // Firebase Analytics emits first_open automatically; do not log the reserved name.
        }

        override fun trackOnboardingStepView(stepName: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.ONBOARDING_STEP_VIEW,
                params = mapOf(KeepAnalyticsParam.STEP_NAME to stepName),
            )
        }

        override fun trackOnboardingStepComplete(stepName: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.ONBOARDING_STEP_COMPLETE,
                params = mapOf(KeepAnalyticsParam.STEP_NAME to stepName),
            )
        }

        override fun trackPermissionOutcome(
            permissionName: String,
            outcome: String,
            stepName: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PERMISSION_OUTCOME,
                params = buildMap {
                    put(KeepAnalyticsParam.PERMISSION_NAME, permissionName)
                    put(KeepAnalyticsParam.OUTCOME, outcome)
                    stepName?.let { put(KeepAnalyticsParam.STEP_NAME, it) }
                },
            )
        }

        override fun trackFirstLockConfigured(
            source: String,
            selectedAppCount: Int?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FIRST_LOCK_CONFIGURED,
                params = buildMap {
                    put(KeepAnalyticsParam.SOURCE, source)
                    selectedAppCount?.let { put(KeepAnalyticsParam.SELECTED_APP_COUNT, it) }
                },
            )
        }

        override fun trackLockSessionStart(
            source: String,
            isRoutine: Boolean?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.LOCK_SESSION_START,
                params = buildMap {
                    put(KeepAnalyticsParam.SOURCE, source)
                    isRoutine?.let { put(KeepAnalyticsParam.IS_ROUTINE, it) }
                },
            )
        }

        override fun trackLockSessionEnd(
            source: String,
            endReason: String,
            isRoutine: Boolean?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.LOCK_SESSION_END,
                params = buildMap {
                    put(KeepAnalyticsParam.SOURCE, source)
                    put(KeepAnalyticsParam.END_REASON, endReason)
                    isRoutine?.let { put(KeepAnalyticsParam.IS_ROUTINE, it) }
                },
            )
        }

        override fun trackEmergencyUnlockUsed(
            source: String,
            unlockCountRemaining: Int?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_USED,
                params = buildMap {
                    put(KeepAnalyticsParam.SOURCE, source)
                    unlockCountRemaining?.let { put(KeepAnalyticsParam.UNLOCK_COUNT_REMAINING, it) }
                },
            )
        }

        override fun trackAppSelectionCompleted(
            selectedAppCount: Int,
            isOnboarding: Boolean,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.APP_SELECTION_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.SELECTED_APP_COUNT to selectedAppCount,
                    KeepAnalyticsParam.IS_ONBOARDING to isOnboarding,
                ),
            )
        }

        override fun trackKeepModeToggled(isEnabled: Boolean) {
            backend.logEvent(
                name = KeepAnalyticsEvent.KEEP_MODE_TOGGLED,
                params = mapOf(KeepAnalyticsParam.IS_ENABLED to isEnabled),
            )
        }

        override fun trackLockScheduled(
            scheduleType: String,
            scheduledDurationMinutes: Long,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.LOCK_SCHEDULED,
                params = mapOf(
                    KeepAnalyticsParam.SCHEDULE_TYPE to scheduleType,
                    KeepAnalyticsParam.SCHEDULED_DURATION_MINUTES to scheduledDurationMinutes,
                ),
            )
        }

        override fun trackAppBlockIntercepted(
            blockSource: String,
            blockedAppPackage: String,
            routineId: String?,
            goalLockId: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                params = buildMap {
                    put(KeepAnalyticsParam.BLOCK_SOURCE, blockSource)
                    put(
                        KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET,
                        blockedAppCategoryBucketForPackage(blockedAppPackage),
                    )
                    routineId?.let { put(KeepAnalyticsParam.ROUTINE_ID, it) }
                    goalLockId?.let { put(KeepAnalyticsParam.GOAL_LOCK_ID, it) }
                },
            )
        }

        override fun trackEmergencyUnlockCompleted(
            reason: String,
            durationMinutes: Int,
            remainingUnlocks: Int,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.REASON to reason,
                    KeepAnalyticsParam.DURATION_MINUTES to durationMinutes,
                    KeepAnalyticsParam.REMAINING_UNLOCKS to remainingUnlocks,
                ),
            )
        }

        override fun trackEmergencyUnlockSettingsChanged(
            settingName: String,
            valueBucket: String,
            refillMode: String,
            durationCountBucket: String,
            source: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_SETTINGS_CHANGED,
                params = mapOf(
                    KeepAnalyticsParam.SETTING_NAME to settingName,
                    KeepAnalyticsParam.VALUE_BUCKET to valueBucket,
                    KeepAnalyticsParam.REFILL_MODE to refillMode,
                    KeepAnalyticsParam.DURATION_COUNT_BUCKET to durationCountBucket,
                    KeepAnalyticsParam.SOURCE to source,
                ),
            )
        }

        override fun trackEmergencyUnlockManualResetRequested(
            remainingUnlocksBucket: String,
            source: String,
            resetResult: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.EMERGENCY_UNLOCK_MANUAL_RESET_REQUESTED,
                params = buildMap {
                    put(KeepAnalyticsParam.REFILL_MODE, AnalyticsEmergencyUnlockRefillMode.MANUAL)
                    put(KeepAnalyticsParam.REMAINING_UNLOCKS_BUCKET, remainingUnlocksBucket)
                    put(KeepAnalyticsParam.SOURCE, source)
                    resetResult?.let { put(KeepAnalyticsParam.RESET_RESULT, it) }
                },
            )
        }

        override fun trackFirstCoreActionCompleted(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
            goalLockId: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED,
                params = coreActionParams(
                    elapsedSinceFirstOpenSeconds = elapsedSinceFirstOpenSeconds,
                    blockingMode = blockingMode,
                    blockedAppPackage = blockedAppPackage,
                    routineId = routineId,
                    goalLockId = goalLockId,
                ),
            )
        }

        override fun trackCoreActionCompleted(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
            goalLockId: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.CORE_ACTION_COMPLETED,
                params = coreActionParams(
                    elapsedSinceFirstOpenSeconds = elapsedSinceFirstOpenSeconds,
                    blockingMode = blockingMode,
                    blockedAppPackage = blockedAppPackage,
                    routineId = routineId,
                    goalLockId = goalLockId,
                ),
            )
        }

        override fun trackFcmTokenCaptured() {
            backend.logEvent(KeepAnalyticsEvent.FCM_TOKEN_CAPTURED)
        }

        override fun trackDeviceRegistrationAttempted() {
            backend.logEvent(KeepAnalyticsEvent.DEVICE_REGISTRATION_ATTEMPTED)
        }

        override fun trackDeviceRegistrationSkipped(reason: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.DEVICE_REGISTRATION_SKIPPED,
                params = mapOf(KeepAnalyticsParam.REASON to reason),
            )
        }

        override fun reviewPromptEligible() {
            backend.logEvent(KeepAnalyticsEvent.REVIEW_PROMPT_ELIGIBLE)
        }

        override fun reviewPromptShown() {
            backend.logEvent(KeepAnalyticsEvent.REVIEW_PROMPT_SHOWN)
        }

        override fun reviewPromptSkipped(reason: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.REVIEW_PROMPT_SKIPPED,
                params = mapOf(KeepAnalyticsParam.REASON to reason),
            )
        }

        override fun reviewPromptFailed(error: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.REVIEW_PROMPT_FAILED,
                params = mapOf(KeepAnalyticsParam.ERROR to error),
            )
        }

        override fun trackFocusSummaryShareTapped(
            periodType: String,
            sessionCountBucket: String,
            durationMinutesBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_TAPPED,
                params = focusSummaryShareParams(
                    periodType = periodType,
                    sessionCountBucket = sessionCountBucket,
                    durationMinutesBucket = durationMinutesBucket,
                ),
            )
        }

        override fun trackFocusSummaryShareSheetOpened(
            periodType: String,
            sessionCountBucket: String,
            durationMinutesBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_SHEET_OPENED,
                params = focusSummaryShareParams(
                    periodType = periodType,
                    sessionCountBucket = sessionCountBucket,
                    durationMinutesBucket = durationMinutesBucket,
                ),
            )
        }

        override fun trackFocusSummaryShareFailed(
            periodType: String,
            reason: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FOCUS_SUMMARY_SHARE_FAILED,
                params = mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to periodType,
                    KeepAnalyticsParam.REASON to reason,
                ),
            )
        }

        override fun trackLockHistoryPerformanceSummaryViewed(
            periodType: String,
            reportState: String,
            sessionCountBucket: String,
            durationMinutesBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.LOCK_HISTORY_PERFORMANCE_SUMMARY_VIEWED,
                params = mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to periodType,
                    KeepAnalyticsParam.REPORT_STATE to reportState,
                    KeepAnalyticsParam.SESSION_COUNT_BUCKET to sessionCountBucket,
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket,
                ),
            )
        }

        override fun trackLockHistoryTopAppsViewed(
            periodType: String,
            topAppsCountBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.LOCK_HISTORY_TOP_APPS_VIEWED,
                params = mapOf(
                    KeepAnalyticsParam.PERIOD_TYPE to periodType,
                    KeepAnalyticsParam.TOP_APPS_COUNT_BUCKET to topAppsCountBucket,
                ),
            )
        }

        override fun trackMonetizationInterestShown(
            interestSurface: String,
            interestContext: String,
            interestVariant: String?,
            purchaseAvailable: Boolean?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.MONETIZATION_INTEREST_SHOWN,
                params = monetizationInterestParams(
                    interestSurface = interestSurface,
                    interestContext = interestContext,
                    interestVariant = interestVariant,
                    purchaseAvailable = purchaseAvailable,
                ),
            )
        }

        override fun trackMonetizationInterestClicked(
            interestSurface: String,
            interestContext: String,
            interestVariant: String?,
            purchaseAvailable: Boolean?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.MONETIZATION_INTEREST_CLICKED,
                params = monetizationInterestParams(
                    interestSurface = interestSurface,
                    interestContext = interestContext,
                    interestVariant = interestVariant,
                    purchaseAvailable = purchaseAvailable,
                ),
            )
        }

        override fun trackSupportContactStarted(surface: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.SUPPORT_CONTACT_STARTED,
                params = mapOf(KeepAnalyticsParam.SURFACE to surface),
            )
        }

        override fun trackSupportContactFallbackUsed(
            surface: String,
            fallbackType: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.SUPPORT_CONTACT_FALLBACK_USED,
                params = mapOf(
                    KeepAnalyticsParam.SURFACE to surface,
                    KeepAnalyticsParam.FALLBACK_TYPE to fallbackType,
                ),
            )
        }

        override fun trackRoutineTemplateShareTapped(
            templateCategory: String,
            repeatDaysBucket: String,
            timeWindowBucket: String,
            routineNameIncluded: Boolean,
        ) {
            log(
                RoutineAnalyticsEvents.templateShareTapped(
                    templateCategory = templateCategory,
                    repeatDaysBucket = repeatDaysBucket,
                    timeWindowBucket = timeWindowBucket,
                    routineNameIncluded = routineNameIncluded,
                ),
            )
        }

        override fun trackRoutineTemplateShareSheetOpened(
            templateCategory: String,
            repeatDaysBucket: String,
            timeWindowBucket: String,
            routineNameIncluded: Boolean,
        ) {
            log(
                RoutineAnalyticsEvents.templateShareSheetOpened(
                    templateCategory = templateCategory,
                    repeatDaysBucket = repeatDaysBucket,
                    timeWindowBucket = timeWindowBucket,
                    routineNameIncluded = routineNameIncluded,
                ),
            )
        }

        override fun trackRoutineTemplateShareFailed(
            templateCategory: String,
            reason: String,
        ) {
            log(
                RoutineAnalyticsEvents.templateShareFailed(
                    templateCategory = templateCategory,
                    reason = reason,
                ),
            )
        }

        override fun trackParentModeDurationSelected(durationMinutesBucket: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_DURATION_SELECTED,
                params = mapOf(KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket),
            )
        }

        override fun trackParentModeAllowedAppsSelected(allowedAppCountBucket: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_ALLOWED_APPS_SELECTED,
                params = mapOf(KeepAnalyticsParam.ALLOWED_APP_COUNT_BUCKET to allowedAppCountBucket),
            )
        }

        override fun trackParentModeStarted(
            durationMinutesBucket: String,
            allowedAppCountBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_STARTED,
                params = mapOf(
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket,
                    KeepAnalyticsParam.ALLOWED_APP_COUNT_BUCKET to allowedAppCountBucket,
                ),
            )
        }

        override fun trackParentModeCompleted(
            durationMinutesBucket: String,
            endReason: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket,
                    KeepAnalyticsParam.END_REASON to endReason,
                ),
            )
        }

        override fun trackParentModeUnlockedByPin(
            pinResult: String,
            endReason: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_UNLOCKED_BY_PIN,
                params = mapOf(
                    KeepAnalyticsParam.PIN_RESULT to pinResult,
                    KeepAnalyticsParam.END_REASON to endReason,
                ),
            )
        }

        override fun trackParentModeExtended(extensionMinutesBucket: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_EXTENDED,
                params = mapOf(KeepAnalyticsParam.EXTENSION_MINUTES_BUCKET to extensionMinutesBucket),
            )
        }

        override fun trackParentModeBlockIntercepted(blockContext: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_BLOCK_INTERCEPTED,
                params = mapOf(KeepAnalyticsParam.BLOCK_CONTEXT to blockContext),
            )
        }

        override fun trackParentModeCancelled(endReason: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.PARENT_MODE_CANCELLED,
                params = mapOf(KeepAnalyticsParam.END_REASON to endReason),
            )
        }

        override fun trackGoalLockCreateStarted(entrySurface: String) {
            backend.logEvent(
                name = KeepAnalyticsEvent.GOAL_LOCK_CREATE_STARTED,
                params = mapOf(KeepAnalyticsParam.ENTRY_SURFACE to entrySurface),
            )
        }

        override fun trackGoalLockCreated(
            durationSelectionType: String,
            lockMode: String,
            selectedAppCountBucket: String,
            goalNameType: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.GOAL_LOCK_CREATED,
                params = mapOf(
                    KeepAnalyticsParam.DURATION_SELECTION_TYPE to durationSelectionType,
                    KeepAnalyticsParam.LOCK_MODE to lockMode,
                    KeepAnalyticsParam.SELECTED_APP_COUNT_BUCKET to selectedAppCountBucket,
                    KeepAnalyticsParam.GOAL_NAME_TYPE to goalNameType,
                ),
            )
        }

        override fun trackGoalLockEndedEarly(
            lockMode: String,
            elapsedDaysBucket: String,
            reason: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.GOAL_LOCK_ENDED_EARLY,
                params = mapOf(
                    KeepAnalyticsParam.LOCK_MODE to lockMode,
                    KeepAnalyticsParam.ELAPSED_DAYS_BUCKET to elapsedDaysBucket,
                    KeepAnalyticsParam.REASON to reason,
                ),
            )
        }

        override fun trackGoalLockCompleted(
            lockMode: String,
            durationDaysBucket: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.GOAL_LOCK_COMPLETED,
                params = mapOf(
                    KeepAnalyticsParam.LOCK_MODE to lockMode,
                    KeepAnalyticsParam.DURATION_DAYS_BUCKET to durationDaysBucket,
                ),
            )
        }

        override fun trackGoalLockUpdated(
            lockMode: String,
            changedField: String,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.GOAL_LOCK_UPDATED,
                params = mapOf(
                    KeepAnalyticsParam.LOCK_MODE to lockMode,
                    KeepAnalyticsParam.CHANGED_FIELD to changedField,
                ),
            )
        }

        override fun trackRepeatBlockRoutineSuggestionShown(
            surface: String,
            suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
        ) {
            log(RoutineAnalyticsEvents.repeatBlockSuggestionShown(surface, suggestion))
        }

        override fun trackRepeatBlockRoutineSuggestionClicked(
            surface: String,
            suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
        ) {
            log(RoutineAnalyticsEvents.repeatBlockSuggestionClicked(surface, suggestion))
        }

        override fun trackRepeatBlockRoutineSuggestionDismissed(
            surface: String,
            suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
        ) {
            log(RoutineAnalyticsEvents.repeatBlockSuggestionDismissed(surface, suggestion))
        }

        override fun trackRepeatBlockRoutineSuggestionApplied(
            surface: String,
            suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
        ) {
            log(RoutineAnalyticsEvents.repeatBlockSuggestionApplied(surface, suggestion))
        }

        override fun trackInstallReferrerAttributionChecked(attribution: AcquisitionAttribution) {
            backend.logEvent(
                name = KeepAnalyticsEvent.INSTALL_REFERRER_ATTRIBUTION_CHECKED,
                params = attribution.toAnalyticsParams(),
            )
        }

        private fun focusSummaryShareParams(
            periodType: String,
            sessionCountBucket: String,
            durationMinutesBucket: String,
        ) = mapOf(
            KeepAnalyticsParam.PERIOD_TYPE to periodType,
            KeepAnalyticsParam.SESSION_COUNT_BUCKET to sessionCountBucket,
            KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket,
        )

        private fun monetizationInterestParams(
            interestSurface: String,
            interestContext: String,
            interestVariant: String?,
            purchaseAvailable: Boolean?,
        ): Map<String, Any?> = buildMap {
            put(KeepAnalyticsParam.INTEREST_SURFACE, interestSurface)
            put(KeepAnalyticsParam.INTEREST_CONTEXT, interestContext)
            interestVariant?.let { put(KeepAnalyticsParam.INTEREST_VARIANT, it) }
            purchaseAvailable?.let { put(KeepAnalyticsParam.PURCHASE_AVAILABLE, it) }
        }

        private fun coreActionParams(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
            goalLockId: String?,
        ): Map<String, Any?> = buildMap {
            put(KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS, elapsedSinceFirstOpenSeconds)
            put(KeepAnalyticsParam.BLOCKING_MODE, blockingMode)
            put(KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET, blockedAppCategoryBucketForPackage(blockedAppPackage))
            routineId?.let { put(KeepAnalyticsParam.ROUTINE_ID, it) }
            goalLockId?.let { put(KeepAnalyticsParam.GOAL_LOCK_ID, it) }
        }
    }
