package com.uiery.keep.analytics

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
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                params = buildMap {
                    put(KeepAnalyticsParam.BLOCK_SOURCE, blockSource)
                    put(KeepAnalyticsParam.BLOCKED_APP_PACKAGE, blockedAppPackage)
                    routineId?.let { put(KeepAnalyticsParam.ROUTINE_ID, it) }
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

        override fun trackFirstCoreActionCompleted(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED,
                params = coreActionParams(
                    elapsedSinceFirstOpenSeconds = elapsedSinceFirstOpenSeconds,
                    blockingMode = blockingMode,
                    blockedAppPackage = blockedAppPackage,
                    routineId = routineId,
                ),
            )
        }

        override fun trackCoreActionCompleted(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
        ) {
            backend.logEvent(
                name = KeepAnalyticsEvent.CORE_ACTION_COMPLETED,
                params = coreActionParams(
                    elapsedSinceFirstOpenSeconds = elapsedSinceFirstOpenSeconds,
                    blockingMode = blockingMode,
                    blockedAppPackage = blockedAppPackage,
                    routineId = routineId,
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

        private fun focusSummaryShareParams(
            periodType: String,
            sessionCountBucket: String,
            durationMinutesBucket: String,
        ) = mapOf(
            KeepAnalyticsParam.PERIOD_TYPE to periodType,
            KeepAnalyticsParam.SESSION_COUNT_BUCKET to sessionCountBucket,
            KeepAnalyticsParam.DURATION_MINUTES_BUCKET to durationMinutesBucket,
        )

        private fun coreActionParams(
            elapsedSinceFirstOpenSeconds: Long,
            blockingMode: String,
            blockedAppPackage: String,
            routineId: String?,
        ): Map<String, Any?> = buildMap {
            put(KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS, elapsedSinceFirstOpenSeconds)
            put(KeepAnalyticsParam.BLOCKING_MODE, blockingMode)
            put(KeepAnalyticsParam.BLOCKED_APP_PACKAGE, blockedAppPackage)
            routineId?.let { put(KeepAnalyticsParam.ROUTINE_ID, it) }
        }
    }
