package com.uiery.keep.notification

import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroSession
import com.uiery.keep.util.formatMinuteSecondCountdown
import java.time.Instant

/**
 * 진행 알림에 실을 값. 리소스를 읽지 않는 순수 계산이라 JVM 테스트로 고정할 수 있다.
 *
 * [alerts] 는 이 갱신이 소리/진동을 내야 하는지다. **구간이 바뀌는 순간에만 참**이다. 초 단위
 * 갱신마다 알리면 25분 동안 폰이 계속 울린다.
 */
internal data class PomodoroNotificationContent(
    val phase: PomodoroPhase,
    val remainingClock: String,
    val cycleIndex: Int,
    val cyclesPerSession: Int,
    val alerts: Boolean,
) {
    companion object {
        fun from(
            session: PomodoroSession,
            now: Instant,
            previousPhase: PomodoroPhase?,
        ): PomodoroNotificationContent {
            val remainingSeconds = PomodoroPolicy.remaining(session = session, now = now).seconds
            return PomodoroNotificationContent(
                phase = session.phase,
                remainingClock = formatMinuteSecondCountdown(
                    remainingSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                ),
                cycleIndex = session.cycleIndex,
                cyclesPerSession = session.cycle.cycles,
                // 첫 표시는 알리지 않는다. 세션을 방금 시작한 사용자는 화면을 보고 있다.
                alerts = previousPhase != null && previousPhase != session.phase,
            )
        }
    }
}
