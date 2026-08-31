package com.uiery.keep.domain.pomodoro

/**
 * 마지막으로 알림에 그린 구간을 기억한다.
 *
 * 진행 알림은 초마다 갱신되지만 소리는 구간이 바뀔 때만 나야 한다. 그 판단에 필요한 유일한 상태다.
 */
internal class PomodoroNotificationPhaseTracker {

    @Volatile
    var lastPhase: PomodoroPhase? = null
        private set

    fun record(phase: PomodoroPhase) {
        lastPhase = phase
    }

    fun reset() {
        lastPhase = null
    }
}
