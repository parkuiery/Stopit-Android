package com.uiery.keep.domain.pomodoro

import java.time.Instant

/**
 * 저장된 낱개 값들과 [PomodoroSession] 사이의 변환.
 *
 * DataStore 를 끌어오지 않는 순수 함수로 둔다. 세션이 살아 있는지 여부가 차단 판정을 좌우하므로,
 * 반쯤 쓰인 값에서 세션이 되살아나는 경로가 있으면 사용자가 예약하지 않은 잠금이 생긴다.
 * **필드 하나라도 없거나 읽을 수 없으면 세션은 없는 것으로 본다.**
 *
 * 사이클 길이는 프리셋 key 가 아니라 분 값으로 저장된다. 프리셋 정의가 나중에 바뀌어도 이미 돌고
 * 있던 세션의 남은 시간이 소급해서 달라지지 않아야 하고, 커스텀 길이는 애초에 표에 없다.
 */
internal object PomodoroSessionCodec {

    fun decode(
        presetKind: String?,
        focusMinutes: Int?,
        shortBreakMinutes: Int?,
        longBreakMinutes: Int?,
        cycles: Int?,
        startedAt: String?,
        phase: String?,
        cycleIndex: Int?,
        phaseDeadline: String?,
        completedFocusCount: Int?,
        status: String?,
    ): PomodoroSession? {
        val decodedKind = PomodoroPresetKind.fromAnalyticsKey(presetKind) ?: return null
        val decodedCycle = PomodoroCycle.restore(
            kind = decodedKind,
            focusMinutes = focusMinutes ?: return null,
            shortBreakMinutes = shortBreakMinutes ?: return null,
            longBreakMinutes = longBreakMinutes ?: return null,
            cycles = cycles ?: return null,
        ) ?: return null
        val decodedPhase = PomodoroPhase.fromAnalyticsKey(phase) ?: return null
        val decodedStatus = PomodoroSessionStatus.fromAnalyticsKey(status) ?: return null
        val decodedStartedAt = parseInstant(startedAt) ?: return null
        val decodedDeadline = parseInstant(phaseDeadline) ?: return null
        val decodedCycleIndex = cycleIndex?.takeIf { it >= 1 } ?: return null
        val decodedCompleted = completedFocusCount?.takeIf { it >= 0 } ?: return null

        return PomodoroSession(
            cycle = decodedCycle,
            startedAt = decodedStartedAt,
            phase = decodedPhase,
            cycleIndex = decodedCycleIndex,
            phaseDeadline = decodedDeadline,
            completedFocusCount = decodedCompleted,
            status = decodedStatus,
        )
    }

    fun encodeStartedAt(session: PomodoroSession): String = session.startedAt.toString()

    fun encodePhaseDeadline(session: PomodoroSession): String = session.phaseDeadline.toString()

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }
}
