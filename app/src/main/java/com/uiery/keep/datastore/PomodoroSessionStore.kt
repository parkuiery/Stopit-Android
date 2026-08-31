package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import com.uiery.keep.domain.pomodoro.PomodoroPresetKind
import com.uiery.keep.domain.pomodoro.PomodoroSession
import com.uiery.keep.domain.pomodoro.PomodoroSessionCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 뽀모도로 세션의 저장 경계.
 *
 * 여기서 나오는 세션은 **저장된 그대로**이고 아직 벽시계 기준으로 따라잡히지 않았다. 읽는 쪽에서
 * `PomodoroPolicy.resolve(...)` 를 거쳐야 지금 상태가 된다. 저장 시점과 읽는 시점 사이에 몇
 * 시간이 지나 있을 수 있기 때문이다.
 */
@Singleton
internal class PomodoroSessionStore
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
    ) {
        val storedSession: Flow<PomodoroSession?> =
            dataStore.data.map { preferences -> preferences.toStoredSession() }

        suspend fun readStoredSession(): PomodoroSession? = storedSession.first()

        suspend fun save(session: PomodoroSession) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.POMODORO_PRESET] = session.cycle.kind.analyticsKey
                preferences[PreferencesKey.POMODORO_FOCUS_MINUTES] = session.cycle.focusMinutes
                preferences[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES] = session.cycle.shortBreakMinutes
                preferences[PreferencesKey.POMODORO_LONG_BREAK_MINUTES] = session.cycle.longBreakMinutes
                preferences[PreferencesKey.POMODORO_CYCLES] = session.cycle.cycles
                preferences[PreferencesKey.POMODORO_STARTED_AT] =
                    PomodoroSessionCodec.encodeStartedAt(session)
                preferences[PreferencesKey.POMODORO_PHASE] = session.phase.analyticsKey
                preferences[PreferencesKey.POMODORO_CYCLE_INDEX] = session.cycleIndex
                preferences[PreferencesKey.POMODORO_PHASE_DEADLINE] =
                    PomodoroSessionCodec.encodePhaseDeadline(session)
                preferences[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT] = session.completedFocusCount
                preferences[PreferencesKey.POMODORO_STATUS] = session.status.analyticsKey
            }
        }

        /**
         * 세션 자체만 지운다. 오늘 집중 횟수는 세션이 아니라 하루에 매달린 값이므로 남긴다.
         */
        suspend fun clearSession() {
            dataStore.edit { preferences ->
                preferences.remove(PreferencesKey.POMODORO_PRESET)
                preferences.remove(PreferencesKey.POMODORO_FOCUS_MINUTES)
                preferences.remove(PreferencesKey.POMODORO_SHORT_BREAK_MINUTES)
                preferences.remove(PreferencesKey.POMODORO_LONG_BREAK_MINUTES)
                preferences.remove(PreferencesKey.POMODORO_CYCLES)
                preferences.remove(PreferencesKey.POMODORO_STARTED_AT)
                preferences.remove(PreferencesKey.POMODORO_PHASE)
                preferences.remove(PreferencesKey.POMODORO_CYCLE_INDEX)
                preferences.remove(PreferencesKey.POMODORO_PHASE_DEADLINE)
                preferences.remove(PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT)
                preferences.remove(PreferencesKey.POMODORO_STATUS)
            }
        }

        suspend fun readTodayFocusCount(today: LocalDate): Int {
            val preferences = dataStore.data.first()
            return preferences.todayFocusCountFor(today)
        }

        /**
         * 오늘 집중 횟수를 [delta] 만큼 올린다. 저장된 날짜가 오늘이 아니면 0에서 다시 센다.
         */
        suspend fun addTodayFocusCount(delta: Int, today: LocalDate) {
            if (delta <= 0) return
            dataStore.edit { preferences ->
                val current = preferences.todayFocusCountFor(today)
                preferences[PreferencesKey.POMODORO_TODAY_DATE] = today.toString()
                preferences[PreferencesKey.POMODORO_TODAY_FOCUS_COUNT] = current + delta
            }
        }

        /**
         * 휴식 중에도 막을지. 기본값은 계속 막는 쪽이다.
         *
         * 이 앱을 고른 이유가 차단이므로 기본은 유지하되, 뽀모도로 카테고리의 관례대로 쉬는 동안
         * 열어 두고 싶은 사용자에게 끌 수 있는 길을 준다.
         */
        val blockDuringBreaks: Flow<Boolean> = dataStore.data.map { preferences ->
            preferences[PreferencesKey.POMODORO_BLOCK_DURING_BREAKS] ?: DEFAULT_BLOCK_DURING_BREAKS
        }

        suspend fun readBlockDuringBreaks(): Boolean = blockDuringBreaks.first()

        suspend fun setBlockDuringBreaks(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.POMODORO_BLOCK_DURING_BREAKS] = enabled
            }
        }

        /**
         * 마지막으로 고른 사이클. 없으면 `null`.
         *
         * 다시 쓸 때 고르는 과정을 건너뛰기 위한 값이라 세션이 끝나도 남는다.
         */
        val lastCycle: Flow<PomodoroCycle?> =
            dataStore.data.map { preferences -> preferences.toLastCycle() }

        suspend fun readLastCycle(): PomodoroCycle? = lastCycle.first()

        suspend fun saveLastCycle(cycle: PomodoroCycle) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.POMODORO_LAST_PRESET] = cycle.kind.analyticsKey
                preferences[PreferencesKey.POMODORO_LAST_FOCUS_MINUTES] = cycle.focusMinutes
                preferences[PreferencesKey.POMODORO_LAST_SHORT_BREAK_MINUTES] = cycle.shortBreakMinutes
                preferences[PreferencesKey.POMODORO_LAST_LONG_BREAK_MINUTES] = cycle.longBreakMinutes
                preferences[PreferencesKey.POMODORO_LAST_CYCLES] = cycle.cycles
            }
        }

        private fun Preferences.toLastCycle(): PomodoroCycle? {
            val kind = PomodoroPresetKind.fromAnalyticsKey(this[PreferencesKey.POMODORO_LAST_PRESET])
                ?: return null
            return PomodoroCycle.restore(
                kind = kind,
                focusMinutes = this[PreferencesKey.POMODORO_LAST_FOCUS_MINUTES] ?: return null,
                shortBreakMinutes = this[PreferencesKey.POMODORO_LAST_SHORT_BREAK_MINUTES] ?: return null,
                longBreakMinutes = this[PreferencesKey.POMODORO_LAST_LONG_BREAK_MINUTES] ?: return null,
                cycles = this[PreferencesKey.POMODORO_LAST_CYCLES] ?: PomodoroCycle.DEFAULT_CYCLES,
            )
        }

        private fun Preferences.toStoredSession(): PomodoroSession? = PomodoroSessionCodec.decode(
            presetKind = this[PreferencesKey.POMODORO_PRESET],
            focusMinutes = this[PreferencesKey.POMODORO_FOCUS_MINUTES],
            shortBreakMinutes = this[PreferencesKey.POMODORO_SHORT_BREAK_MINUTES],
            longBreakMinutes = this[PreferencesKey.POMODORO_LONG_BREAK_MINUTES],
            cycles = this[PreferencesKey.POMODORO_CYCLES],
            startedAt = this[PreferencesKey.POMODORO_STARTED_AT],
            phase = this[PreferencesKey.POMODORO_PHASE],
            cycleIndex = this[PreferencesKey.POMODORO_CYCLE_INDEX],
            phaseDeadline = this[PreferencesKey.POMODORO_PHASE_DEADLINE],
            completedFocusCount = this[PreferencesKey.POMODORO_COMPLETED_FOCUS_COUNT],
            status = this[PreferencesKey.POMODORO_STATUS],
        )

        companion object {
            internal const val DEFAULT_BLOCK_DURING_BREAKS = true
        }

        private fun Preferences.todayFocusCountFor(today: LocalDate): Int {
            val storedDate = this[PreferencesKey.POMODORO_TODAY_DATE]
            return if (storedDate == today.toString()) {
                this[PreferencesKey.POMODORO_TODAY_FOCUS_COUNT] ?: 0
            } else {
                0
            }
        }
    }
