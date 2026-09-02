package com.uiery.keep.feature.pomodoro

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsPomodoroEntrySurface
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroPresetKind
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import com.uiery.keep.domain.pomodoro.PomodoroSession
import com.uiery.keep.domain.pomodoro.PomodoroSessionController
import com.uiery.keep.domain.pomodoro.PomodoroStartResult
import com.uiery.keep.domain.pomodoro.PomodoroSessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Instant
import javax.inject.Inject

internal data class PomodoroUiState(
    val isLoading: Boolean = true,
    val selectedAppCount: Int = 0,
    val selectedCycle: PomodoroCycle = PomodoroCycle.Default,
    // 커스텀 카드를 고르기 전에도 스테퍼가 움직여야 하므로 선택과 초안을 분리해 둔다.
    val customFocusMinutes: Int = DEFAULT_CUSTOM_FOCUS_MINUTES,
    val customShortBreakMinutes: Int = DEFAULT_CUSTOM_SHORT_BREAK_MINUTES,
    val customLongBreakMinutes: Int = DEFAULT_CUSTOM_LONG_BREAK_MINUTES,
    val customCycles: Int = PomodoroCycle.DEFAULT_CYCLES,
    val session: PomodoroSession? = null,
    val remainingSeconds: Int = 0,
    val todayFocusCount: Int = 0,
    val isEndConfirmVisible: Boolean = false,
    val blockDuringBreaks: Boolean = true,
) {
    val phase: PomodoroPhase? get() = session?.takeIf { !it.status.isFinished }?.phase

    val isSessionRunning: Boolean get() = session?.status == PomodoroSessionStatus.Active

    val isSessionFinished: Boolean get() = session?.status?.isFinished == true

    /** 시간표를 끝까지 돈 세션과 사용자가 중간에 끊은 세션은 다른 이야기다. */
    val isSessionEndedEarly: Boolean get() = session?.status == PomodoroSessionStatus.EndedEarly

    val phaseTotalMinutes: Int
        get() = session?.let { PomodoroPolicy.phaseDuration(it).toMinutes().toInt() } ?: 0

    /** 남은 시간 비율. 링 진행도가 남은 시간을 보여준다는 계약을 여기 한 곳에서 잡는다. */
    val remainingFraction: Float
        get() {
            val total = session?.let { PomodoroPolicy.phaseDuration(it).seconds } ?: return 0f
            if (total <= 0L) return 0f
            return (remainingSeconds.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val completedFocusCount: Int get() = session?.completedFocusCount ?: 0

    val completedFocusMinutes: Int
        get() = session?.let { PomodoroPolicy.completedFocusDuration(it).toMinutes().toInt() } ?: 0

    val nextFocusCycleIndex: Int? get() = session?.let { PomodoroPolicy.nextFocusCycleIndex(it) }

    val cyclesPerSession: Int get() = session?.cycle?.cycles ?: selectedCycle.cycles

    val isCustomSelected: Boolean get() = selectedCycle.kind == PomodoroPresetKind.Custom
}

/**
 * 스테퍼 한 칸의 크기.
 *
 * 도메인은 1분 단위까지 허용하지만 화면은 5분 단위로만 움직인다. 25분에서 50분까지 25번 눌러야
 * 하는 입력은 쓰이지 않는다.
 */
internal const val CUSTOM_MINUTES_STEP = 5

internal const val DEFAULT_CUSTOM_FOCUS_MINUTES = 25
internal const val DEFAULT_CUSTOM_SHORT_BREAK_MINUTES = 5
internal const val DEFAULT_CUSTOM_LONG_BREAK_MINUTES = 15

internal sealed interface PomodoroSideEffect {
    data object StartSessionService : PomodoroSideEffect

    /** 잠금을 세우지 못해 세션이 시작되지 않았다. */
    data object StartBlocked : PomodoroSideEffect
    data object StopSessionService : PomodoroSideEffect
    data object NavigateHome : PomodoroSideEffect
}

@HiltViewModel
internal class PomodoroViewModel
    @Inject
    constructor(
        private val controller: PomodoroSessionController,
        private val blockingStateStore: BlockingStateStore,
    ) : ContainerHost<PomodoroUiState, PomodoroSideEffect>, ViewModel() {

        override val container: Container<PomodoroUiState, PomodoroSideEffect> =
            container(PomodoroUiState())

        /**
         * 화면이 보일 때마다 저장된 세션을 지금 상태로 따라잡는다.
         *
         * 서비스가 죽어 있었거나 프로세스가 새로 떴을 수 있으므로, 화면은 자기가 기억하는 상태가
         * 아니라 저장소를 다시 읽는다.
         */
        fun refresh() = intent {
            val now = Instant.now()
            val session = controller.sync(now = now)
            val selectedAppCount = blockingStateStore.accessibilitySnapshot.first().selectedAppPackages.size
            // reduce 블록은 suspend 가 아니다. 저장소 읽기는 미리 끝내 놓고 상태만 갈아끼운다.
            val todayFocusCount = controller.todayFocusCount(now = now)
            // 마지막으로 쓰던 사이클로 열린다. 매번 다시 고르게 하면 반복 사용이 느려진다.
            val lastCycle = controller.lastCycleOrDefault()
            val blockDuringBreaks = controller.blockDuringBreaks()
            reduce {
                state.copy(
                    isLoading = false,
                    selectedAppCount = selectedAppCount,
                    selectedCycle = lastCycle,
                    blockDuringBreaks = blockDuringBreaks,
                    customFocusMinutes = lastCycle.focusMinutes,
                    customShortBreakMinutes = lastCycle.shortBreakMinutes,
                    customLongBreakMinutes = lastCycle.longBreakMinutes,
                    customCycles = lastCycle.cycles,
                    session = session,
                    remainingSeconds = session.remainingSeconds(now),
                    todayFocusCount = todayFocusCount,
                )
            }
            if (session?.status?.isFinished == true) {
                postSideEffect(PomodoroSideEffect.StopSessionService)
            }
        }

        fun selectCycle(cycle: PomodoroCycle) = intent {
            reduce { state.copy(selectedCycle = cycle) }
        }

        /** 커스텀 카드를 고른다. 현재 초안 값이 그대로 선택이 된다. */
        fun selectCustomCycle() = intent {
            val cycle = state.customCycleOrNull() ?: return@intent
            reduce { state.copy(selectedCycle = cycle) }
        }

        fun changeCustomFocusMinutes(delta: Int) = intent {
            val next = (state.customFocusMinutes + delta)
                .coerceIn(PomodoroCycle.FOCUS_MINUTES_RANGE)
            reduce { state.copy(customFocusMinutes = next).reapplyCustomIfSelected() }
        }

        fun changeCustomShortBreakMinutes(delta: Int) = intent {
            val next = (state.customShortBreakMinutes + delta)
                .coerceIn(PomodoroCycle.SHORT_BREAK_MINUTES_RANGE)
            reduce { state.copy(customShortBreakMinutes = next).reapplyCustomIfSelected() }
        }

        /**
         * 반복 횟수. 이 값이 총 잠금 시간을 가장 크게 흔든다 — 4회 고정이던 시절에는 25/5 를 고르면
         * 무조건 2시간 10분이었다.
         */
        fun changeCustomCycles(delta: Int) = intent {
            val next = (state.customCycles + delta).coerceIn(PomodoroCycle.CYCLES_RANGE)
            reduce { state.copy(customCycles = next).reapplyCustomIfSelected() }
        }

        fun changeCustomLongBreakMinutes(delta: Int) = intent {
            val next = (state.customLongBreakMinutes + delta)
                .coerceIn(PomodoroCycle.LONG_BREAK_MINUTES_RANGE)
            reduce { state.copy(customLongBreakMinutes = next).reapplyCustomIfSelected() }
        }

        private fun PomodoroUiState.customCycleOrNull(): PomodoroCycle? = PomodoroCycle.custom(
            focusMinutes = customFocusMinutes,
            shortBreakMinutes = customShortBreakMinutes,
            longBreakMinutes = customLongBreakMinutes,
            cycles = customCycles,
        )

        /**
         * 커스텀이 이미 선택된 상태에서 스테퍼를 움직이면 선택도 함께 따라와야 한다.
         * 그러지 않으면 화면에 보이는 값과 실제로 시작될 세션이 어긋난다.
         */
        private fun PomodoroUiState.reapplyCustomIfSelected(): PomodoroUiState {
            if (!isCustomSelected) return this
            val cycle = customCycleOrNull() ?: return this
            return copy(selectedCycle = cycle)
        }

        fun startSession(entrySurface: String = AnalyticsPomodoroEntrySurface.HOME) = intent {
            // 막을 앱이 없으면 세션은 아무것도 지키지 못한다. 잠긴 것처럼 보이는 화면만 남는다.
            if (state.selectedAppCount == 0) return@intent

            val now = Instant.now()
            val snapshot = blockingStateStore.accessibilitySnapshot.first()
            val result = controller.start(
                cycle = state.selectedCycle,
                entrySurface = entrySurface,
                selectedPackages = snapshot.selectedAppPackages,
                hasWebTargets = false,
                now = now,
            )
            // 잠금을 세우지 못하면 세션도 시작하지 않는다. 이미 다른 타이머 잠금이 도는 상태에서
            // 세션 화면만 켜지면, 화면은 집중 중이라고 말하는데 실제 잠금은 다른 것이 쥐고 있다.
            val session = (result as? PomodoroStartResult.Started)?.session ?: run {
                postSideEffect(PomodoroSideEffect.StartBlocked)
                return@intent
            }
            reduce {
                state.copy(
                    session = session,
                    remainingSeconds = session.remainingSeconds(now),
                    isEndConfirmVisible = false,
                )
            }
            postSideEffect(PomodoroSideEffect.StartSessionService)
        }

        /**
         * 끝난 세션이 남아 있으면 치우고 새로 시작한다.
         *
         * 시트에서 바로 시작할 때 쓴다 — 사용자는 "집중 시작"을 눌렀는데 지난 세션 결과 화면이
         * 뜨면 자기가 뭘 눌렀는지 알 수 없다.
         */
        fun startFresh(entrySurface: String = AnalyticsPomodoroEntrySurface.HOME) = intent {
            controller.clearFinishedSession()
            reduce { state.copy(session = null, remainingSeconds = 0) }
            startSession(entrySurface)
        }

        /** 화면이 열려 있는 동안 1초마다 호출된다. 상태 전이는 저장소를 통해서만 일어난다. */
        fun tick() = intent {
            val current = state.session ?: return@intent
            if (current.status.isFinished) return@intent

            val now = Instant.now()
            val synced = controller.sync(now = now) ?: return@intent
            val todayFocusCount = controller.todayFocusCount(now = now)
            reduce {
                state.copy(
                    session = synced,
                    remainingSeconds = synced.remainingSeconds(now),
                    todayFocusCount = todayFocusCount,
                )
            }
            if (synced.status.isFinished) {
                postSideEffect(PomodoroSideEffect.StopSessionService)
            }
        }

        /**
         * 휴식 중에도 막을지. 세션이 도는 중에 바꿔도 즉시 반영된다 — 차단 판정이 저장된 값을
         * 매번 다시 읽기 때문이다.
         */
        fun setBlockDuringBreaks(enabled: Boolean) = intent {
            controller.setBlockDuringBreaks(enabled)
            reduce { state.copy(blockDuringBreaks = enabled) }
        }

        fun showEndConfirm() = intent { reduce { state.copy(isEndConfirmVisible = true) } }

        fun hideEndConfirm() = intent { reduce { state.copy(isEndConfirmVisible = false) } }

        fun confirmEnd() = intent {
            val now = Instant.now()
            val ended = controller.endByUser(now = now)
            reduce {
                state.copy(
                    session = ended,
                    remainingSeconds = 0,
                    isEndConfirmVisible = false,
                )
            }
            postSideEffect(PomodoroSideEffect.StopSessionService)
        }

        fun finishAndLeave() = intent {
            controller.clearFinishedSession()
            reduce { state.copy(session = null, remainingSeconds = 0) }
            postSideEffect(PomodoroSideEffect.NavigateHome)
        }

        fun restartSession() = intent {
            controller.clearFinishedSession()
            reduce { state.copy(session = null, remainingSeconds = 0) }
        }

        private fun PomodoroSession?.remainingSeconds(now: Instant): Int {
            val session = this ?: return 0
            return PomodoroPolicy.remaining(session = session, now = now)
                .seconds
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        }
    }
