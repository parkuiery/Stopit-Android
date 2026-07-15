package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeStateStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
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
    fun startedAnalyticsFailureDoesNotTurnPersistedPracticeIntoFailure() = runBlocking {
        val dataStore = FakeDataStore()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = FakeTimedStarter(TimedLockStartResult.Started("deadline", false)),
            practiceStore = FirstPromisePracticeStore(dataStore),
            analytics = PracticeRecordingAnalytics(failStarted = true),
            clock = clock,
        )

        val result = controller.start(draft, routineEnabled = true)

        assertEquals(FirstPromisePracticeStartResult.Started, result)
        assertEquals(draft.draftId, FirstPromisePracticeStore(dataStore).readActiveToken(clock.millis())?.draftId)
    }

    @Test
    fun tokenWriteFailureRollsBackOwnedSessionAndRetryCanStart() = runBlocking {
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline", false))
        val store = FailOncePracticeStateStore(FirstPromisePracticeStore(FakeDataStore()))
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = store,
            analytics = PracticeRecordingAnalytics(),
            clock = clock,
        )

        assertEquals(FirstPromisePracticeStartResult.Failed, controller.start(draft, routineEnabled = true))
        assertEquals(1, timed.rollbackCalls)
        assertEquals(FirstPromisePracticeStartResult.Started, controller.start(draft, routineEnabled = true))
        assertEquals(2, timed.startCalls)
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
    fun skipTracksOnlyOnceAcrossControllerRecreation() = runBlocking {
        val analytics = PracticeRecordingAnalytics()
        val dataStore = FakeDataStore()
        val first = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.AlreadyActive),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )
        val recreated = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.AlreadyActive),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )

        first.skip(draft.draftId, routineEnabled = true)
        recreated.skip(draft.draftId, routineEnabled = true)

        assertEquals(listOf(FirstPromisePracticeOutcome.Skipped), analytics.outcomes)
    }

    @Test
    fun skipDoesNotTrackAfterStartedOrForDisabledRoutine() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibility(true),
            FakeTimedStarter(TimedLockStartResult.Started("deadline", false)),
            FirstPromisePracticeStore(dataStore),
            analytics,
            clock,
        )

        controller.start(draft, routineEnabled = true)
        controller.skip(draft.draftId, routineEnabled = true)
        controller.skip("disabled-draft", routineEnabled = false)

        assertEquals(listOf(FirstPromisePracticeOutcome.Started), analytics.outcomes)
    }

    private fun accessibility(enabled: Boolean): AccessibilityChecker = object : AccessibilityChecker {
        override fun isEnabled(): Boolean = enabled
    }
}

private class FakeTimedStarter(private val result: TimedLockStartResult) : TimedLockStarter {
    var packages: Set<String>? = null
    var durationMinutes: Long? = null
    var origin: TimedLockStartOrigin? = null
    var startCalls = 0
    var rollbackCalls = 0
    override suspend fun start(
        packages: Set<String>,
        durationMinutes: Long,
        origin: TimedLockStartOrigin,
        targetDeadline: Instant?,
    ): TimedLockStartResult {
        startCalls++
        this.packages = packages
        this.durationMinutes = durationMinutes
        this.origin = origin
        return result
    }

    override suspend fun rollback(started: TimedLockStartResult.Started): Boolean {
        rollbackCalls++
        return true
    }
}

private class FailOncePracticeStateStore(
    private val delegate: FirstPromisePracticeStateStore,
) : FirstPromisePracticeStateStore by delegate {
    private var shouldFail = true

    override suspend fun saveStarted(token: FirstPromisePracticeToken) {
        if (shouldFail) {
            shouldFail = false
            error("datastore unavailable")
        }
        delegate.saveStarted(token)
    }
}

private class PracticeRecordingAnalytics(
    private val failStarted: Boolean = false,
) : KeepAnalytics {
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
    override fun trackFirstPromisePracticeOutcome(outcome: FirstPromisePracticeOutcome) {
        outcomes += outcome
        if (failStarted && outcome == FirstPromisePracticeOutcome.Started) error("analytics unavailable")
    }
}
