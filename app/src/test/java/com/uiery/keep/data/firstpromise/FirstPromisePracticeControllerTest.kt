package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromisePracticeOutcome
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.feature.review.AccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromisePracticeControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-16T09:00:00Z"), ZoneOffset.UTC)
    private val draft = FirstPromiseDraft(
        draftId = "draft-local",
        goal = FirstPromiseGoal.Focus,
        packageName = "com.example.focus",
        appLabel = "Focus",
        startMinutes = 22 * 60,
        repeatDays = setOf(1),
        source = FirstPromiseSource.Manual,
    )

    @Test
    fun successStartsSharedTenMinuteBoundaryThenWritesDurableAttributionToken() = runBlocking {
        val dataStore = FakeDataStore()
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline", false))
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = FirstPromisePracticeStore(dataStore),
            analytics = analytics,
            clock = clock,
        )

        val result = controller.start(draft, routineEnabled = true)

        assertEquals(FirstPromisePracticeStartResult.Started, result)
        assertEquals(setOf(draft.packageName), timed.packages)
        assertEquals(10L, timed.durationMinutes)
        assertEquals(TimedLockStartOrigin.FirstPromisePractice, timed.origin)
        val token = FirstPromisePracticeStore(dataStore).readActiveToken(clock.millis())
        assertEquals(draft.draftId, token?.draftId)
        assertEquals(clock.millis() + 10 * 60_000L, token?.expiresAtMillis)
        assertEquals(listOf(FirstPromisePracticeOutcome.Started), analytics.outcomes)
    }

    @Test
    fun disabledRoutineOffersNoPracticeOutcome() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.Started("deadline", false)),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )

        assertEquals(FirstPromisePracticeStartResult.RoutineDisabled, controller.start(draft, false))
        assertTrue(analytics.outcomes.isEmpty())
        assertNull(FirstPromisePracticeStore(dataStore).readActiveToken(clock.millis()))
    }

    @Test
    fun accessibilityOrActiveLockFailureRecordsStartFailedAndLeavesRetryable() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = PracticeRecordingAnalytics()
        val inaccessible = FirstPromisePracticeController(
            accessibility(false),
            FakeTimedStarter(TimedLockStartResult.Started("deadline", false)),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )
        val active = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.AlreadyActive),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )

        assertEquals(FirstPromisePracticeStartResult.AccessibilityRequired, inaccessible.start(draft, true))
        assertEquals(FirstPromisePracticeStartResult.ActiveTimedLock, active.start(draft, true))
        assertEquals(
            listOf(FirstPromisePracticeOutcome.StartFailed, FirstPromisePracticeOutcome.StartFailed),
            analytics.outcomes,
        )
    }

    @Test
    fun skipTracksOnlyOncePerController() = runBlocking {
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.AlreadyActive),
            FirstPromisePracticeStore(FakeDataStore()),
            analytics,
            clock,
        )

        controller.skip()
        controller.skip()

        assertEquals(listOf(FirstPromisePracticeOutcome.Skipped), analytics.outcomes)
    }

    private fun accessibility(enabled: Boolean): AccessibilityChecker = object : AccessibilityChecker {
        override fun isEnabled(): Boolean = enabled
    }
}

private class FakeTimedStarter(private val result: TimedLockStartResult) : TimedLockStarter {
    var packages: Set<String>? = null
    var durationMinutes: Long? = null
    var origin: TimedLockStartOrigin? = null
    override suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant?,
    ): TimedLockStartResult {
        this.packages = packages
        this.durationMinutes = durationMinutes
        this.origin = origin
        return result
    }
}

private class PracticeRecordingAnalytics : KeepAnalytics {
    val outcomes = mutableListOf<FirstPromisePracticeOutcome>()
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
    override fun trackFirstPromisePracticeOutcome(outcome: FirstPromisePracticeOutcome) { outcomes += outcome }
}
