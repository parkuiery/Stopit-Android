package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineCreationCtaAnalyticsTest {
    private val backend = RecordingRoutineCreationCtaBackend()
    private val analytics = FirebaseKeepAnalytics(backend)

    @Test
    fun routineCreationCtaEventsUsePrivacySafeExperimentParamsOnly() {
        analytics.trackRoutineCreationCtaShown(
            surface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
            activationStage = AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
            hasRoutine = false,
            ctaVariant = AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
        )
        analytics.trackRoutineCreationCtaClicked(
            surface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
            activationStage = AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
            hasRoutine = false,
            ctaVariant = AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
        )
        analytics.trackRoutineCreationCtaDismissed(
            surface = AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
            activationStage = AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
            hasRoutine = false,
            ctaVariant = AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
        )

        assertEquals(
            listOf(
                LoggedRoutineCreationCtaEvent(
                    name = KeepAnalyticsEvent.ROUTINE_CREATION_CTA_SHOWN,
                    params = routineCreationCtaParams(),
                ),
                LoggedRoutineCreationCtaEvent(
                    name = KeepAnalyticsEvent.ROUTINE_CREATION_CTA_CLICKED,
                    params = routineCreationCtaParams(),
                ),
                LoggedRoutineCreationCtaEvent(
                    name = KeepAnalyticsEvent.ROUTINE_CREATION_CTA_DISMISSED,
                    params = routineCreationCtaParams(),
                ),
            ),
            backend.loggedEvents,
        )
    }

    private fun routineCreationCtaParams(): Map<String, Any?> = mapOf(
        KeepAnalyticsParam.SURFACE to AnalyticsRoutineCreationCtaSurface.HOME_SECONDARY,
        KeepAnalyticsParam.ACTIVATION_STAGE to AnalyticsRoutineCreationCtaActivationStage.POST_FIRST_CORE_ACTION,
        KeepAnalyticsParam.HAS_ROUTINE to false,
        KeepAnalyticsParam.CTA_VARIANT to AnalyticsRoutineCreationCtaVariant.SOFT_DEFAULT,
    )
}

private data class LoggedRoutineCreationCtaEvent(
    val name: String,
    val params: Map<String, Any?>,
)

private class RecordingRoutineCreationCtaBackend : AnalyticsBackend {
    val loggedEvents = mutableListOf<LoggedRoutineCreationCtaEvent>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        loggedEvents += LoggedRoutineCreationCtaEvent(name = name, params = params)
    }

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit
}
