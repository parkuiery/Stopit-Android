package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
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
            LoggedEvent(
                name = KeepAnalyticsEvent.FIRST_OPEN,
                params = emptyMap(),
            ),
            backend.loggedEvents[2],
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
