package com.uiery.keep.domain.pomodoro

import com.uiery.keep.datastore.PomodoroSessionStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PomodoroSessionStoreTest {

    private val session = PomodoroSession(
        cycle = PomodoroCycle.Focus50,
        startedAt = Instant.parse("2026-08-27T09:00:00Z"),
        phase = PomodoroPhase.ShortBreak,
        cycleIndex = 2,
        phaseDeadline = Instant.parse("2026-08-27T11:00:00Z"),
        completedFocusCount = 2,
        status = PomodoroSessionStatus.Active,
    )

    @Test
    fun saveAndReadRoundTripsEveryFieldAsEnumKeysAndInstants() = runBlocking {
        val dataStore = FakeDataStore()
        val store = PomodoroSessionStore(dataStore)

        store.save(session)

        assertEquals(session, store.readStoredSession())
        val snapshot = dataStore.snapshot()
        assertEquals("50_10", snapshot[PreferencesKey.POMODORO_PRESET])
        // 길이는 프리셋 key 로 되찾는 게 아니라 값으로 남는다.
        assertEquals(50, snapshot[PreferencesKey.POMODORO_FOCUS_MINUTES])
        assertEquals(10, snapshot[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES])
        assertEquals(20, snapshot[PreferencesKey.POMODORO_LONG_BREAK_MINUTES])
        assertEquals("short_break", snapshot[PreferencesKey.POMODORO_PHASE])
        assertEquals("active", snapshot[PreferencesKey.POMODORO_STATUS])
        // deadline 은 로컬 시각이 아니라 Instant 문자열이어야 한다.
        assertEquals("2026-08-27T11:00:00Z", snapshot[PreferencesKey.POMODORO_PHASE_DEADLINE])
    }

    /**
     * 반쯤 쓰인 값에서 세션이 되살아나면 사용자가 예약하지 않은 잠금이 켜진다.
     */
    @Test
    fun aPartiallyWrittenSessionReadsAsNoSession() = runBlocking {
        val store = PomodoroSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.POMODORO_PRESET] = "25_5"
                this[PreferencesKey.POMODORO_PHASE] = "focus"
                this[PreferencesKey.POMODORO_STATUS] = "active"
                // 시작 시각과 deadline 이 없다.
            },
        )

        assertNull(store.readStoredSession())
    }

    @Test
    fun anUnknownPresetOrPhaseReadsAsNoSession() = runBlocking {
        val store = PomodoroSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.POMODORO_PRESET] = "90_20"
                this[PreferencesKey.POMODORO_FOCUS_MINUTES] = 90
                this[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES] = 20
                this[PreferencesKey.POMODORO_LONG_BREAK_MINUTES] = 30
                this[PreferencesKey.POMODORO_STARTED_AT] = "2026-08-27T09:00:00Z"
                this[PreferencesKey.POMODORO_PHASE] = "focus"
                this[PreferencesKey.POMODORO_CYCLE_INDEX] = 1
                this[PreferencesKey.POMODORO_PHASE_DEADLINE] = "2026-08-27T09:25:00Z"
                this[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT] = 0
                this[PreferencesKey.POMODORO_STATUS] = "active"
            },
        )

        assertNull(store.readStoredSession())
    }

    @Test
    fun anUnparsableDeadlineReadsAsNoSession() = runBlocking {
        val store = PomodoroSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.POMODORO_PRESET] = "25_5"
                this[PreferencesKey.POMODORO_FOCUS_MINUTES] = 25
                this[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES] = 5
                this[PreferencesKey.POMODORO_LONG_BREAK_MINUTES] = 15
                this[PreferencesKey.POMODORO_STARTED_AT] = "2026-08-27T09:00:00Z"
                this[PreferencesKey.POMODORO_PHASE] = "focus"
                this[PreferencesKey.POMODORO_CYCLE_INDEX] = 1
                // 예전 저장 형식처럼 timezone 이 없는 값은 받지 않는다.
                this[PreferencesKey.POMODORO_PHASE_DEADLINE] = "2026-08-27T09:25:00"
                this[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT] = 0
                this[PreferencesKey.POMODORO_STATUS] = "active"
            },
        )

        assertNull(store.readStoredSession())
    }

    @Test
    fun aCustomCycleRoundTripsWithItsOwnLengths() = runBlocking {
        val dataStore = FakeDataStore()
        val store = PomodoroSessionStore(dataStore)
        val custom = session.copy(
            cycle = PomodoroCycle.custom(
                focusMinutes = 40,
                shortBreakMinutes = 7,
                longBreakMinutes = 25,
            )!!,
        )

        store.save(custom)

        assertEquals(custom, store.readStoredSession())
        assertEquals("custom", dataStore.snapshot()[PreferencesKey.POMODORO_PRESET])
        assertEquals(40, dataStore.snapshot()[PreferencesKey.POMODORO_FOCUS_MINUTES])
    }

    /**
     * 저장된 길이가 허용 범위를 벗어나 있으면 세션 없음으로 본다. 손상된 값으로 되살아난 세션이
     * 사용자가 예약한 적 없는 길이의 잠금을 만드는 경로를 막는다.
     */
    @Test
    fun storedLengthsOutsideTheAllowedRangeReadAsNoSession() = runBlocking {
        val store = PomodoroSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.POMODORO_PRESET] = "custom"
                this[PreferencesKey.POMODORO_FOCUS_MINUTES] = 600
                this[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES] = 5
                this[PreferencesKey.POMODORO_LONG_BREAK_MINUTES] = 15
                this[PreferencesKey.POMODORO_STARTED_AT] = "2026-08-27T09:00:00Z"
                this[PreferencesKey.POMODORO_PHASE] = "focus"
                this[PreferencesKey.POMODORO_CYCLE_INDEX] = 1
                this[PreferencesKey.POMODORO_PHASE_DEADLINE] = "2026-08-27T19:00:00Z"
                this[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT] = 0
                this[PreferencesKey.POMODORO_STATUS] = "active"
            },
        )

        assertNull(store.readStoredSession())
    }

    @Test
    fun aSessionMissingItsLengthsReadsAsNoSession() = runBlocking {
        val store = PomodoroSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.POMODORO_PRESET] = "25_5"
                this[PreferencesKey.POMODORO_STARTED_AT] = "2026-08-27T09:00:00Z"
                this[PreferencesKey.POMODORO_PHASE] = "focus"
                this[PreferencesKey.POMODORO_CYCLE_INDEX] = 1
                this[PreferencesKey.POMODORO_PHASE_DEADLINE] = "2026-08-27T09:25:00Z"
                this[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT] = 0
                this[PreferencesKey.POMODORO_STATUS] = "active"
            },
        )

        assertNull(store.readStoredSession())
    }

    @Test
    fun clearSessionRemovesTheSessionButKeepsTodaysCount() = runBlocking {
        val store = PomodoroSessionStore(FakeDataStore())
        val today = LocalDate.of(2026, 8, 27)
        store.save(session)
        store.addTodayFocusCount(delta = 2, today = today)

        store.clearSession()

        assertNull(store.readStoredSession())
        assertEquals(2, store.readTodayFocusCount(today))
    }

    @Test
    fun todaysCountRestartsWhenTheDateChanges() = runBlocking {
        val store = PomodoroSessionStore(FakeDataStore())
        val today = LocalDate.of(2026, 8, 27)
        store.addTodayFocusCount(delta = 3, today = today)

        assertEquals(3, store.readTodayFocusCount(today))
        assertEquals(0, store.readTodayFocusCount(today.plusDays(1)))

        store.addTodayFocusCount(delta = 1, today = today.plusDays(1))
        assertEquals(1, store.readTodayFocusCount(today.plusDays(1)))
    }

    @Test
    fun nonPositiveDeltasNeverMoveTheCount() = runBlocking {
        val store = PomodoroSessionStore(FakeDataStore())
        val today = LocalDate.of(2026, 8, 27)

        store.addTodayFocusCount(delta = 0, today = today)
        store.addTodayFocusCount(delta = -2, today = today)

        assertEquals(0, store.readTodayFocusCount(today))
    }
}
