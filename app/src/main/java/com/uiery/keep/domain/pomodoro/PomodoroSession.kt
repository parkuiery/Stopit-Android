package com.uiery.keep.domain.pomodoro

import java.time.Duration
import java.time.Instant

/**
 * 사이클 길이를 고른 방식.
 *
 * analytics enum 이자 저장값이다. 프리셋 정의가 나중에 바뀌더라도 이 key 의 의미는 바꾸지 않는다.
 * [Custom] 은 길이 자체를 담지 않는다 — 임의의 분 값은 enum 이 될 수 없고, analytics 에는 원본
 * 분 값 대신 bucket 만 나간다.
 */
internal enum class PomodoroPresetKind(val analyticsKey: String) {
    Focus25("25_5"),
    Focus50("50_10"),
    Custom("custom"),
    ;

    companion object {
        fun fromAnalyticsKey(key: String?): PomodoroPresetKind? =
            entries.firstOrNull { it.analyticsKey == key }
    }
}

/**
 * 한 세션이 도는 집중/휴식 길이.
 *
 * 프리셋과 커스텀이 같은 타입이다. 정책·알림·화면은 어느 쪽인지 몰라도 되고, [kind] 는 analytics
 * 와 화면 라벨에만 쓰인다.
 *
 * **길이는 세션에 값으로 저장된다.** 프리셋 key 만 저장해 두고 읽을 때 표에서 찾으면, 나중에
 * 프리셋 정의를 바꿨을 때 이미 돌고 있던 세션의 남은 시간이 소급해서 달라진다.
 */
internal data class PomodoroCycle(
    val kind: PomodoroPresetKind,
    val focus: Duration,
    val shortBreak: Duration,
    val longBreak: Duration,
    /** 이 세션이 도는 집중 횟수. 마지막 집중 뒤에 긴 휴식이 오고 세션이 끝난다. */
    val cycles: Int = DEFAULT_CYCLES,
) {
    companion object {
        val Focus25 = PomodoroCycle(
            kind = PomodoroPresetKind.Focus25,
            focus = Duration.ofMinutes(25),
            shortBreak = Duration.ofMinutes(5),
            longBreak = Duration.ofMinutes(15),
            cycles = DEFAULT_CYCLES,
        )

        val Focus50 = PomodoroCycle(
            kind = PomodoroPresetKind.Focus50,
            focus = Duration.ofMinutes(50),
            shortBreak = Duration.ofMinutes(10),
            longBreak = Duration.ofMinutes(20),
            cycles = DEFAULT_CYCLES,
        )

        val Default = Focus25

        val presets: List<PomodoroCycle> = listOf(Focus25, Focus50)

        /**
         * 사용자가 직접 정한 길이. 범위를 벗어나면 `null` 이다.
         *
         * 상한이 없으면 "집중 600분" 같은 값으로 하루짜리 잠금을 만들 수 있고, 그건 집중 세션이
         * 아니라 목표 잠금이 할 일이다. 하한이 없으면 1초짜리 세션으로 완주 지표를 채울 수 있다.
         */
        fun custom(
            focusMinutes: Int,
            shortBreakMinutes: Int,
            longBreakMinutes: Int,
            cycles: Int = DEFAULT_CYCLES,
        ): PomodoroCycle? {
            if (focusMinutes !in FOCUS_MINUTES_RANGE) return null
            if (shortBreakMinutes !in SHORT_BREAK_MINUTES_RANGE) return null
            if (longBreakMinutes !in LONG_BREAK_MINUTES_RANGE) return null
            if (cycles !in CYCLES_RANGE) return null
            return PomodoroCycle(
                kind = PomodoroPresetKind.Custom,
                focus = Duration.ofMinutes(focusMinutes.toLong()),
                shortBreak = Duration.ofMinutes(shortBreakMinutes.toLong()),
                longBreak = Duration.ofMinutes(longBreakMinutes.toLong()),
                cycles = cycles,
            )
        }

        /** 저장된 값에서 되살린다. 범위를 벗어난 값은 세션 없음으로 본다. */
        fun restore(
            kind: PomodoroPresetKind,
            focusMinutes: Int,
            shortBreakMinutes: Int,
            longBreakMinutes: Int,
            cycles: Int = DEFAULT_CYCLES,
        ): PomodoroCycle? = custom(
            focusMinutes = focusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            cycles = cycles,
        )?.copy(kind = kind)

        const val DEFAULT_CYCLES = 4

        val FOCUS_MINUTES_RANGE = 5..120

        /**
         * 집중 1회는 사이클이 아니다 — 반복이 없는 한 번의 잠금은 타이머 세그먼트가 하는 일이다.
         * 상한은 커스텀 최대치(집중 120분 × 8 + 휴식)가 하루를 넘지 않게 잡았다.
         */
        val CYCLES_RANGE = 2..8
        val SHORT_BREAK_MINUTES_RANGE = 1..60
        val LONG_BREAK_MINUTES_RANGE = 1..120
    }

    val focusMinutes: Int get() = focus.toMinutes().toInt()

    val shortBreakMinutes: Int get() = shortBreak.toMinutes().toInt()

    val longBreakMinutes: Int get() = longBreak.toMinutes().toInt()
}

/**
 * 세션 안의 현재 구간.
 *
 * 휴식도 세션의 일부이며 **차단이 유지되는 구간**이다. 휴식을 "차단이 풀린 시간"으로 읽지 않는다.
 */
internal enum class PomodoroPhase(val analyticsKey: String) {
    Focus("focus"),
    ShortBreak("short_break"),
    LongBreak("long_break"),
    ;

    val isBreak: Boolean get() = this != Focus

    /** `pomodoro_break_started.break_type`. 집중 구간에는 값이 없다. */
    val breakTypeAnalyticsKey: String?
        get() = when (this) {
            Focus -> null
            ShortBreak -> "short"
            LongBreak -> "long"
        }

    companion object {
        fun fromAnalyticsKey(key: String?): PomodoroPhase? =
            entries.firstOrNull { it.analyticsKey == key }
    }
}

internal enum class PomodoroSessionStatus(val analyticsKey: String) {
    Active("active"),
    Completed("completed"),
    EndedEarly("ended_early"),
    ;

    val isFinished: Boolean get() = this != Active

    companion object {
        fun fromAnalyticsKey(key: String?): PomodoroSessionStatus? =
            entries.firstOrNull { it.analyticsKey == key }
    }
}

/**
 * 진행 중이거나 방금 끝난 뽀모도로 세션 하나.
 *
 * [phaseDeadline]은 `Instant`다. `docs/MANUAL_TIMER_LOCK_DEADLINE_CONTRACT.md`와 같은 이유로
 * timezone이 바뀌어도 같은 실제 시각에 만료되어야 하고, 앱이 죽어 있던 동안 지나간 구간은
 * 저장된 deadline만 보고 벽시계 기준으로 따라잡을 수 있어야 한다.
 */
internal data class PomodoroSession(
    val cycle: PomodoroCycle,
    val startedAt: Instant,
    val phase: PomodoroPhase,
    val cycleIndex: Int,
    val phaseDeadline: Instant,
    val completedFocusCount: Int,
    val status: PomodoroSessionStatus,
)
