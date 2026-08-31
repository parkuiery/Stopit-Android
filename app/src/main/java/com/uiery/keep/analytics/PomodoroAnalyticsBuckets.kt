package com.uiery.keep.analytics

/**
 * 뽀모도로 analytics 의 enum/bucket 사전.
 *
 * `docs/POMODORO_FOCUS_MVP.md` 의 "Analytics 계약"이 원본이다. raw 사이클 번호나 raw 분 값,
 * 앱 이름/package 는 여기를 지나가지 않는다.
 */
object AnalyticsPomodoroEntrySurface {
    const val HOME = "home"
    const val MENU = "menu"
}

object AnalyticsPomodoroEndReason {
    /** 마지막 긴 휴식까지 끝났다. */
    const val ALL_CYCLES_COMPLETED = "all_cycles_completed"

    /** 사용자가 직접 끝냈다. */
    const val USER_ENDED = "user_ended"

    /** 앱이 죽어 있는 동안 세션이 시간표의 끝을 지났고, 돌아왔을 때 발견했다. */
    const val EXPIRED_RECOVERY = "expired_recovery"
}

object AnalyticsPomodoroBreakType {
    const val SHORT = "short"
    const val LONG = "long"
}

/**
 * `cycle_index_bucket` / `completed_focus_count_bucket` / `elapsed_minutes_bucket` 계산.
 *
 * 경계값이 조용히 바뀌면 배포 전후 수치를 비교할 수 없게 되므로 순수 함수로 두고 테스트로 고정한다.
 */
object PomodoroAnalyticsBuckets {

    /**
     * 커스텀 길이는 `preset` enum 으로 알 수 없으므로 이 bucket 이 유일한 길이 신호다.
     *
     * 경계는 두 프리셋이 서로 다른 칸에 떨어지도록 잡았다 — 25분은 `25_34`, 50분은 `50_plus`.
     * 프리셋과 커스텀의 분포를 같은 축에서 비교할 수 있어야 커스텀이 실제로 쓰이는지 읽힌다.
     */
    fun focusMinutesBucket(focusMinutes: Int): String = when {
        focusMinutes < 15 -> "0_14"
        focusMinutes < 25 -> "15_24"
        focusMinutes < 35 -> "25_34"
        focusMinutes < 50 -> "35_49"
        else -> "50_plus"
    }

    /**
     * 세션이 도는 반복 횟수. 총 잠금 시간을 가장 크게 흔드는 값이라 길이 bucket 과 함께 봐야
     * 사용자가 실제로 얼마를 거는지 읽힌다.
     */
    fun cycleCountBucket(cycles: Int): String = when {
        cycles <= 2 -> "2"
        cycles == 3 -> "3"
        cycles == 4 -> "4"
        cycles <= 6 -> "5_6"
        else -> "7_plus"
    }

    fun cycleIndexBucket(cycleIndex: Int): String = when {
        cycleIndex <= 1 -> "1"
        cycleIndex <= 3 -> "2_3"
        cycleIndex <= 6 -> "4_6"
        else -> "7_plus"
    }

    fun completedFocusCountBucket(completedFocusCount: Int): String = when {
        completedFocusCount <= 0 -> "0"
        completedFocusCount == 1 -> "1"
        completedFocusCount <= 3 -> "2_3"
        completedFocusCount <= 6 -> "4_6"
        else -> "7_plus"
    }

    fun elapsedMinutesBucket(elapsedMinutes: Long): String = when {
        elapsedMinutes < 10 -> "0_9"
        elapsedMinutes < 30 -> "10_29"
        elapsedMinutes < 60 -> "30_59"
        elapsedMinutes < 120 -> "60_119"
        else -> "120_plus"
    }

    fun selectedAppCountBucket(selectedAppCount: Int): String = when {
        selectedAppCount <= 1 -> "1"
        selectedAppCount <= 3 -> "2_3"
        selectedAppCount <= 6 -> "4_6"
        else -> "7_plus"
    }
}
