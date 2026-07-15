package com.uiery.keep.data.firstpromise

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.lock.TimedLockSessionController
import com.uiery.keep.data.lock.TimedLockStartOrigin
import com.uiery.keep.data.lock.TimedLockStartResult
import com.uiery.keep.data.lock.TimedLockStarter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromisePracticeDecision
import com.uiery.keep.datastore.FirstPromisePracticeAttempt
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeStateStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromisePracticeOutcome
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.feature.review.AccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
        val practiceStore = FirstPromisePracticeStore(dataStore)
        val timed = FakeTimedStarter(
            TimedLockStartResult.Started("deadline", false),
            onCommit = {
                assertEquals(
                    FirstPromisePracticeDecision.Started,
                    practiceStore.readDecision(draft.draftId),
                )
            },
        )
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = practiceStore,
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
        assertEquals(1, timed.commitCalls)
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
        assertEquals(0, timed.commitCalls)
        assertEquals(FirstPromisePracticeStartResult.Started, controller.start(draft, routineEnabled = true))
        assertEquals(2, timed.startCalls)
        assertEquals(1, timed.commitCalls)
    }

    @Test
    fun tokenFailureRestoresPreviousSessionSnapshotWithoutSuccessAnalytics() = runBlocking {
        val previousDeadline = ManualLockTimePolicy.encodeDeadline(clock.instant().minusSeconds(60))
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.previous"),
                PreferencesKey.START_TIME to 123L,
                PreferencesKey.LOCK_TIME to previousDeadline,
            ),
        )
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = TimedLockSessionController(BlockingStateStore(dataStore), analytics, clock),
            practiceStore = AlwaysFailPracticeStateStore(),
            analytics = analytics,
            clock = clock,
        )

        assertEquals(FirstPromisePracticeStartResult.Failed, controller.start(draft, true))

        val restored = dataStore.snapshot()
        assertEquals(setOf("com.example.previous"), restored[PreferencesKey.SELECTED_APP_PACKAGES])
        assertEquals(123L, restored[PreferencesKey.START_TIME])
        assertEquals(previousDeadline, restored[PreferencesKey.LOCK_TIME])
        assertTrue(analytics.lockCalls.isEmpty())
        assertTrue(FirstPromisePracticeOutcome.Started !in analytics.outcomes)
    }

    @Test
    fun cancellationBeforeStartedDecisionRollsBackWithoutSuccessAnalyticsAndRethrows() {
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline", false))
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = CancellingPracticeStateStore(commitBeforeCancel = false),
            analytics = analytics,
            clock = clock,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.start(draft, true) }
        }

        assertEquals(1, timed.rollbackCalls)
        assertEquals(0, timed.commitCalls)
        assertTrue(analytics.lockCalls.isEmpty())
        assertTrue(FirstPromisePracticeOutcome.Started !in analytics.outcomes)
    }

    @Test
    fun staleExpiredStartedAttemptNeverSuppressesSecondAttemptCompensation() {
        val dataStore = FakeDataStore()
        val durableStore = FirstPromisePracticeStore(dataStore)
        runBlocking {
            durableStore.saveStarted(
                FirstPromisePracticeToken(
                    draftId = draft.draftId,
                    startedAtMillis = 1L,
                    expiresAtMillis = 2L,
                    attemptId = "attempt-prior",
                    encodedDeadline = "deadline-prior",
                ),
            )
            durableStore.readActiveToken(clock.millis())
        }
        val store = AttemptCancellingPracticeStateStore(
            delegate = durableStore,
            mode = AttemptCancellationMode.BeforeSave,
        )
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline-current", false))
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = store,
            analytics = analytics,
            clock = clock,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.start(draft, true) }
        }

        assertEquals(1, timed.rollbackCalls)
        assertEquals(0, timed.commitCalls)
        assertFalse(runBlocking { durableStore.isStartedAttemptCommitted(store.currentAttempt()) })
        assertTrue(analytics.lockCalls.isEmpty())
        assertTrue(FirstPromisePracticeOutcome.Started !in analytics.outcomes)
    }

    @Test
    fun exactCurrentAttemptCommittedBeforeCancellationKeepsItsOwnedSession() {
        val durableStore = FirstPromisePracticeStore(FakeDataStore())
        val store = AttemptCancellingPracticeStateStore(
            delegate = durableStore,
            mode = AttemptCancellationMode.CommitThenCancel,
        )
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline-current", false))
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = store,
            analytics = PracticeRecordingAnalytics(),
            clock = clock,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.start(draft, true) }
        }

        assertEquals(0, timed.rollbackCalls)
        assertTrue(runBlocking { durableStore.isStartedAttemptCommitted(store.currentAttempt()) })
        assertEquals(0, timed.commitCalls)
    }

    @Test
    fun proofReadFailureRollsBackAndClearsMatchingPartialAttempt() {
        val dataStore = FakeDataStore()
        val durableStore = FirstPromisePracticeStore(dataStore)
        val store = AttemptCancellingPracticeStateStore(
            delegate = durableStore,
            mode = AttemptCancellationMode.PartialTokenThenCancel,
            failProofReadOnce = true,
        )
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline-current", false))
        val analytics = PracticeRecordingAnalytics()
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = store,
            analytics = analytics,
            clock = clock,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.start(draft, true) }
        }

        assertEquals(1, timed.rollbackCalls)
        assertEquals(0, timed.commitCalls)
        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN))
        assertFalse(runBlocking { durableStore.isStartedAttemptCommitted(store.currentAttempt()) })
        assertTrue(analytics.lockCalls.isEmpty())
        assertTrue(FirstPromisePracticeOutcome.Started !in analytics.outcomes)
    }

    @Test
    fun cancellationAfterStartedDecisionKeepsOwnedSessionButStillRethrows() {
        val timed = FakeTimedStarter(TimedLockStartResult.Started("deadline", false))
        val analytics = PracticeRecordingAnalytics()
        val store = CancellingPracticeStateStore(commitBeforeCancel = true)
        val controller = FirstPromisePracticeController(
            accessibilityChecker = accessibility(true),
            timedLockStarter = timed,
            practiceStore = store,
            analytics = analytics,
            clock = clock,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.start(draft, true) }
        }

        assertEquals(0, timed.rollbackCalls)
        assertEquals(FirstPromisePracticeDecision.Started, runBlocking { store.readDecision(draft.draftId) })
        assertEquals(0, timed.commitCalls)
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

private class FakeTimedStarter(
    private val result: TimedLockStartResult,
    private val onCommit: suspend () -> Unit = {},
) : TimedLockStarter {
    var packages: Set<String>? = null
    var durationMinutes: Long? = null
    var origin: TimedLockStartOrigin? = null
    var startCalls = 0
    var rollbackCalls = 0
    var commitCalls = 0
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

    override suspend fun commit(started: TimedLockStartResult.Started) {
        onCommit()
        commitCalls++
    }
}

private class AlwaysFailPracticeStateStore : FirstPromisePracticeStateStore {
    override suspend fun saveStarted(token: FirstPromisePracticeToken) = error("token unavailable")
    override suspend fun recordSkippedIfAbsent(draftId: String): Boolean = false
    override suspend fun readDecision(draftId: String): FirstPromisePracticeDecision? = null
    override suspend fun isStartedAttemptCommitted(attempt: FirstPromisePracticeAttempt): Boolean = false
    override suspend fun clearAttempt(attempt: FirstPromisePracticeAttempt) = Unit
}

private class CancellingPracticeStateStore(
    private val commitBeforeCancel: Boolean,
) : FirstPromisePracticeStateStore {
    private var decision: FirstPromisePracticeDecision? = null
    private var committedAttempt: FirstPromisePracticeAttempt? = null

    override suspend fun saveStarted(token: FirstPromisePracticeToken) {
        if (commitBeforeCancel) {
            decision = FirstPromisePracticeDecision.Started
            committedAttempt = FirstPromisePracticeAttempt(
                attemptId = token.attemptId,
                draftId = token.draftId,
                encodedDeadline = token.encodedDeadline,
            )
        }
        throw CancellationException("cancel practice")
    }

    override suspend fun recordSkippedIfAbsent(draftId: String): Boolean = false
    override suspend fun readDecision(draftId: String): FirstPromisePracticeDecision? {
        return decision
    }
    override suspend fun isStartedAttemptCommitted(attempt: FirstPromisePracticeAttempt): Boolean =
        committedAttempt == attempt
    override suspend fun clearAttempt(attempt: FirstPromisePracticeAttempt) {
        if (committedAttempt == attempt) {
            committedAttempt = null
            decision = null
        }
    }
}

private enum class AttemptCancellationMode {
    BeforeSave,
    CommitThenCancel,
    PartialTokenThenCancel,
}

private class AttemptCancellingPracticeStateStore(
    private val delegate: FirstPromisePracticeStore,
    private val mode: AttemptCancellationMode,
    private var failProofReadOnce: Boolean = false,
) : FirstPromisePracticeStateStore by delegate {
    private lateinit var attemptedToken: FirstPromisePracticeToken

    override suspend fun saveStarted(token: FirstPromisePracticeToken) {
        attemptedToken = token
        when (mode) {
            AttemptCancellationMode.BeforeSave -> Unit
            AttemptCancellationMode.CommitThenCancel -> delegate.saveStarted(token)
            AttemptCancellationMode.PartialTokenThenCancel -> delegate.saveToken(token)
        }
        throw CancellationException("cancel current practice attempt")
    }

    override suspend fun isStartedAttemptCommitted(attempt: FirstPromisePracticeAttempt): Boolean {
        if (failProofReadOnce) {
            failProofReadOnce = false
            error("proof unavailable")
        }
        return delegate.isStartedAttemptCommitted(attempt)
    }

    override suspend fun clearAttempt(attempt: FirstPromisePracticeAttempt) {
        delegate.clearAttempt(attempt)
    }

    fun currentAttempt(): FirstPromisePracticeAttempt = FirstPromisePracticeAttempt(
        attemptId = attemptedToken.attemptId,
        draftId = attemptedToken.draftId,
        encodedDeadline = attemptedToken.encodedDeadline,
    )
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
    val lockCalls = mutableListOf<String>()
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) {
        lockCalls += "start"
    }
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
    override fun trackLockScheduled(scheduleType: String, scheduledDurationMinutes: Long) {
        lockCalls += "scheduled"
    }
    override fun trackFirstPromisePracticeOutcome(outcome: FirstPromisePracticeOutcome) {
        outcomes += outcome
        if (failStarted && outcome == FirstPromisePracticeOutcome.Started) error("analytics unavailable")
    }
}
