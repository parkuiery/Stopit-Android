package com.uiery.keep.domain.pomodoro

import java.time.Duration
import java.time.Instant

/**
 * 뽀모도로 세션의 진행 규칙.
 *
 * Android framework 없이 도는 순수 함수 묶음이다. 차단 판정, 알림 문안, 화면 상태가 모두 여기서
 * 나오므로 이 파일이 기능의 실제 계약이다.
 *
 * 한 세션은 집중 `cycle.cycles` 번과 그 사이의 휴식으로 이루어지고, 마지막 집중 뒤의 긴 휴식이
 * 끝나면 완료된다. 횟수는 사이클마다 다를 수 있으므로 상수가 아니라 세션에서 읽는다.
 */
internal object PomodoroPolicy {

    /**
     * 한 세션이 거칠 수 있는 구간 전환 횟수의 상한.
     *
     * 집중 N번 + 짧은 휴식 N-1번 + 긴 휴식 1번 = 2N구간이므로 전환은 최대 2N번이다. [resolve]의
     * 따라잡기 루프가 유한하다는 근거이자, 규칙이 바뀌었을 때 조용히 무한 루프가 되지 않게 하는
     * 안전장치다.
     */
    private fun maxPhaseTransitions(session: PomodoroSession): Int = session.cycle.cycles * 2

    fun start(
        cycle: PomodoroCycle,
        now: Instant,
    ): PomodoroSession = PomodoroSession(
        cycle = cycle,
        startedAt = now,
        phase = PomodoroPhase.Focus,
        cycleIndex = 1,
        phaseDeadline = now.plus(cycle.focus),
        completedFocusCount = 0,
        status = PomodoroSessionStatus.Active,
    )

    /**
     * 저장된 세션을 [now] 기준 상태로 따라잡는다.
     *
     * 앱이 죽어 있거나 기기가 꺼져 있던 동안 지나간 구간은 **저장된 deadline을 기준으로** 차례대로
     * 소비된다. `now`에서 다시 재는 것이 아니다. 그래야 자리를 비운 시간이 세션을 늘리지 않는다.
     *
     * 지나간 집중 구간만 [PomodoroSession.completedFocusCount]에 반영되고 휴식은 소비된 것으로 본다.
     */
    fun resolve(
        session: PomodoroSession,
        now: Instant,
    ): PomodoroSession {
        if (session.status.isFinished) return session

        var current = session
        var transitions = 0
        val maxTransitions = maxPhaseTransitions(session)
        while (!now.isBefore(current.phaseDeadline) && transitions < maxTransitions) {
            current = advance(current)
            transitions++
            if (current.status.isFinished) return current
        }
        return current
    }

    /** 사용자가 직접 끝낸 세션. 지금까지 완료한 집중 횟수는 그대로 남는다. */
    fun endEarly(session: PomodoroSession): PomodoroSession =
        if (session.status.isFinished) {
            session
        } else {
            session.copy(status = PomodoroSessionStatus.EndedEarly)
        }

    /**
     * 세션이 아직 살아 있는가.
     *
     * 막는지 여부와는 별개다 — 휴식 중 차단을 끈 사용자에게도 세션은 계속 돌고 있다.
     * 세션이 만든 타이머 잠금이 지금 누구 것인지 판단할 때도 이 값을 쓴다.
     */
    fun isActive(
        session: PomodoroSession?,
        now: Instant,
    ): Boolean {
        val current = session ?: return false
        return !resolve(current, now).status.isFinished
    }

    /**
     * 지금 이 세션이 앱을 막아야 하는가.
     *
     * [blockDuringBreaks] 가 참이면 집중과 휴식을 구분하지 않는다 — 세션이 사는 동안 계속 막는다.
     * 거짓이면 휴식 구간에는 물러난다. 뽀모도로 카테고리의 기본 동작은 후자지만, 이 앱을 고른
     * 이유가 차단이라는 점 때문에 기본값은 전자로 둔다. `docs/POMODORO_FOCUS_MVP.md` 참고.
     */
    fun blocksNow(
        session: PomodoroSession?,
        now: Instant,
        blockDuringBreaks: Boolean,
    ): Boolean {
        val current = session ?: return false
        val resolved = resolve(current, now)
        if (resolved.status.isFinished) return false
        return blockDuringBreaks || !resolved.phase.isBreak
    }

    /** 현재 구간의 남은 시간. 끝난 세션은 0이다. */
    fun remaining(
        session: PomodoroSession,
        now: Instant,
    ): Duration {
        if (session.status.isFinished) return Duration.ZERO
        val remaining = Duration.between(now, session.phaseDeadline)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }

    /** 현재 구간의 전체 길이. 진행률 표시에 쓴다. */
    fun phaseDuration(session: PomodoroSession): Duration = when (session.phase) {
        PomodoroPhase.Focus -> session.cycle.focus
        PomodoroPhase.ShortBreak -> session.cycle.shortBreak
        PomodoroPhase.LongBreak -> session.cycle.longBreak
    }

    /**
     * 세션 하나가 끝까지 도는 데 걸리는 전체 시간.
     *
     * 사용자가 고르는 것은 "집중 25분"이지만 실제로 예약되는 잠금은 집중 4번 + 휴식 4번,
     * 즉 2시간 10분이다. `50/10` 이면 4시간 10분이 된다. 예측 가능한 잠금 상태가 이 앱의
     * 정체성인데 그 숫자를 말하지 않으면 사용자는 자기가 얼마를 거는지 모르고 시작한다.
     */
    fun totalDuration(cycle: PomodoroCycle): Duration = cycle.focus
        .multipliedBy(cycle.cycles.toLong())
        .plus(cycle.shortBreak.multipliedBy((cycle.cycles - 1).toLong()))
        .plus(cycle.longBreak)

    /** 완료한 집중 시간의 합. 완료 화면의 "집중한 시간"이다. */
    fun completedFocusDuration(session: PomodoroSession): Duration =
        session.cycle.focus.multipliedBy(session.completedFocusCount.toLong())

    /**
     * 휴식이 끝나면 시작될 집중의 번호. 휴식 화면의 "다음은 N번째 집중"이다.
     *
     * 집중 구간이거나 이미 끝난 세션에는 다음 집중이 없다.
     */
    fun nextFocusCycleIndex(session: PomodoroSession): Int? = when {
        session.status.isFinished -> null
        session.phase == PomodoroPhase.Focus -> null
        session.phase == PomodoroPhase.LongBreak -> null
        else -> session.cycleIndex + 1
    }

    private fun advance(session: PomodoroSession): PomodoroSession = when (session.phase) {
        PomodoroPhase.Focus -> {
            val completed = session.completedFocusCount + 1
            val isLastCycle = session.cycleIndex >= session.cycle.cycles
            val nextPhase = if (isLastCycle) PomodoroPhase.LongBreak else PomodoroPhase.ShortBreak
            val nextDuration =
                if (isLastCycle) session.cycle.longBreak else session.cycle.shortBreak
            session.copy(
                phase = nextPhase,
                phaseDeadline = session.phaseDeadline.plus(nextDuration),
                completedFocusCount = completed,
            )
        }

        PomodoroPhase.ShortBreak -> session.copy(
            phase = PomodoroPhase.Focus,
            cycleIndex = session.cycleIndex + 1,
            phaseDeadline = session.phaseDeadline.plus(session.cycle.focus),
        )

        // 긴 휴식은 마지막 집중 뒤에만 오므로 그 끝이 곧 세션의 끝이다.
        PomodoroPhase.LongBreak -> session.copy(status = PomodoroSessionStatus.Completed)
    }
}
