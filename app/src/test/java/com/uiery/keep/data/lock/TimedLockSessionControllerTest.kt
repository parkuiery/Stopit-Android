package com.uiery.keep.data.lock

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.PreferencesKey
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

class TimedLockSessionControllerTest {
    private val now = Instant.parse("2026-07-16T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun successAtomicallyPersistsSelectionStartAndDeadlineAndTracksHomeBoundary() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = RecordingAnalytics()
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), analytics, clock)

        val result = controller.start(
            packages = setOf("com.example.focus"),
            durationMinutes = 30,
            origin = TimedLockStartOrigin.Home(TimedLockHomeScheduleType.Countdown),
        )

        assertTrue(result is TimedLockStartResult.Started)
        assertTrue(analytics.calls.isEmpty())
        controller.commit(result as TimedLockStartResult.Started)
        val snapshot = dataStore.snapshot()
        assertEquals(setOf("com.example.focus"), snapshot[PreferencesKey.SELECTED_APP_PACKAGES])
        assertEquals(now.toEpochMilli(), snapshot[PreferencesKey.START_TIME])
        assertTrue(ManualLockTimePolicy.isActiveAt(snapshot[PreferencesKey.LOCK_TIME], now, ZoneOffset.UTC))
        assertEquals(listOf("first:home_timer:1", "scheduled:countdown:30", "start:home_timer"), analytics.calls)
    }

    @Test
    fun rejectsZeroDurationEmptyAppsAndAlreadyActiveWithoutMutation() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(PreferencesKey.LOCK_TIME to ManualLockTimePolicy.encodeDeadline(now.plusSeconds(60))),
        )
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), RecordingAnalytics(), clock)

        assertEquals(TimedLockStartResult.EmptyApps, controller.start(emptySet(), 10, TimedLockStartOrigin.FirstPromisePractice))
        assertEquals(TimedLockStartResult.InvalidDuration, controller.start(setOf("app"), 0, TimedLockStartOrigin.FirstPromisePractice))
        assertEquals(TimedLockStartResult.AlreadyActive, controller.start(setOf("app"), 10, TimedLockStartOrigin.FirstPromisePractice))
        assertNull(dataStore.snapshot()[PreferencesKey.START_TIME])
    }

    @Test
    fun practiceUsesTimedLockAnalyticsButNeverClaimsFirstLockConfigured() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = RecordingAnalytics()
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), analytics, clock)

        val result = controller.start(setOf("com.example.focus"), 10, TimedLockStartOrigin.FirstPromisePractice)

        assertTrue(result is TimedLockStartResult.Started)
        assertTrue(analytics.calls.isEmpty())
        controller.commit(result as TimedLockStartResult.Started)
        assertEquals(listOf("scheduled:countdown:10", "start:home_timer"), analytics.calls)
        assertFalse(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true)
    }

    @Test
    fun positiveSubMinuteTargetStartsEvenWhenDisplayDurationFloorsToZero() = runBlocking {
        val dataStore = FakeDataStore()
        val analytics = RecordingAnalytics()
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), analytics, clock)

        val result = controller.start(
            packages = setOf("com.example.focus"),
            durationMinutes = 0,
            origin = TimedLockStartOrigin.Home(TimedLockHomeScheduleType.Timer),
            targetDeadline = now.plusSeconds(30),
        )

        assertTrue(result is TimedLockStartResult.Started)
        controller.commit(result as TimedLockStartResult.Started)
        assertEquals(ManualLockTimePolicy.encodeDeadline(now.plusSeconds(30)), dataStore.snapshot()[PreferencesKey.LOCK_TIME])
        assertTrue("scheduled:timer:0" in analytics.calls)
    }

    @Test
    fun rollbackClearsOnlyTheSessionOwnedByTheStartResult() = runBlocking {
        val dataStore = FakeDataStore()
        val store = BlockingStateStore(dataStore)
        val controller = TimedLockSessionController(store, RecordingAnalytics(), clock)
        val started = controller.start(
            packages = setOf("com.example.focus"),
            durationMinutes = 10,
            origin = TimedLockStartOrigin.FirstPromisePractice,
        ) as TimedLockStartResult.Started

        assertTrue(controller.rollback(started))
        assertNull(dataStore.snapshot()[PreferencesKey.LOCK_TIME])
        assertNull(dataStore.snapshot()[PreferencesKey.START_TIME])

        store.saveLockTime(ManualLockTimePolicy.encodeDeadline(now.plusSeconds(20 * 60)))
        assertFalse(controller.rollback(started))
        assertEquals(
            ManualLockTimePolicy.encodeDeadline(now.plusSeconds(20 * 60)),
            dataStore.snapshot()[PreferencesKey.LOCK_TIME],
        )
    }

    @Test
    fun homeRollbackReleasesUndeliveredFirstLockReservation() = runBlocking {
        val dataStore = FakeDataStore()
        val store = BlockingStateStore(dataStore)
        val controller = TimedLockSessionController(store, RecordingAnalytics(), clock)
        val started = controller.start(
            packages = setOf("com.example.focus"),
            durationMinutes = 10,
            origin = TimedLockStartOrigin.Home(TimedLockHomeScheduleType.Countdown),
        ) as TimedLockStartResult.Started

        assertEquals(
            AnalyticsSource.HOME_TIMER,
            dataStore.snapshot()[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE],
        )

        assertTrue(controller.rollback(started))

        assertNull(dataStore.snapshot()[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE])
        assertFalse(dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true)
    }

    @Test
    fun rollbackRestoresPreviousSelectionStartAndExpiredDeadlineAtomically() = runBlocking {
        val previousDeadline = ManualLockTimePolicy.encodeDeadline(now.minusSeconds(60))
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.previous"),
                PreferencesKey.START_TIME to 123L,
                PreferencesKey.LOCK_TIME to previousDeadline,
            ),
        )
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), RecordingAnalytics(), clock)
        val started = controller.start(
            packages = setOf("com.example.practice"),
            durationMinutes = 10,
            origin = TimedLockStartOrigin.FirstPromisePractice,
        ) as TimedLockStartResult.Started

        assertTrue(controller.rollback(started))

        val restored = dataStore.snapshot()
        assertEquals(setOf("com.example.previous"), restored[PreferencesKey.SELECTED_APP_PACKAGES])
        assertEquals(123L, restored[PreferencesKey.START_TIME])
        assertEquals(previousDeadline, restored[PreferencesKey.LOCK_TIME])
    }

    @Test
    fun analyticsCancellationAtCommitBoundaryIsRethrown() {
        val dataStore = FakeDataStore()
        val analytics = RecordingAnalytics(scheduledFailure = CancellationException("cancel analytics"))
        val controller = TimedLockSessionController(BlockingStateStore(dataStore), analytics, clock)
        val started = runBlocking {
            controller.start(
                setOf("com.example.focus"),
                10,
                TimedLockStartOrigin.FirstPromisePractice,
            ) as TimedLockStartResult.Started
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { controller.commit(started) }
        }
    }
}

private class RecordingAnalytics(
    private val scheduledFailure: Throwable? = null,
) : KeepAnalytics {
    val calls = mutableListOf<String>()
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
        calls += "first:$source:$selectedAppCount"
    }
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) {
        calls += "start:$source"
    }
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
    override fun trackLockScheduled(scheduleType: String, scheduledDurationMinutes: Long) {
        scheduledFailure?.let { throw it }
        calls += "scheduled:$scheduleType:$scheduledDurationMinutes"
    }
}
