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
            backend.logEvent(KeepAnalyticsEvent.FIRST_OPEN)
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
    }
