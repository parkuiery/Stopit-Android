package com.uiery.keep.feature.pomodoro

/**
 * 집중 세션 화면이 지금 어느 단계에 있는지.
 *
 * 진입은 **소개 → 확인 → 설정** 3단이고(`docs/POMODORO_FOCUS_MVP.md` "진입 3단 계약"),
 * 진행·완료는 세션 상태가 정한다. 이 판단이 컴포저블 안의 `var` 로만 있으면 화면을 띄우지
 * 않고는 확인할 수 없어서, 규칙만 순수 함수로 꺼내 둔다.
 */
internal enum class PomodoroStep {
    /** 기능 소개. 계속할지 여기서 고른다. */
    Intro,

    /** `이렇게 시작할게요` + 요약 카드 + 시작 버튼. */
    Confirm,

    /** 사이클·휴식 중 차단·막을 앱. **시작 버튼은 없다.** */
    Settings,

    Running,
    Complete,
    Loading,
}

/**
 * 화면 단계 판단.
 *
 * 세션 상태가 화면 안의 선택보다 **먼저**다. 세션이 도는 중이라면 소개나 설정을 보여줄 이유가
 * 없다 — 사용자가 설정을 열어 둔 채로 세션이 시작되는 경로(빠른 시작)가 실제로 있다.
 */
internal object PomodoroStepPolicy {

    fun resolve(
        isLoading: Boolean,
        isSessionRunning: Boolean,
        isSessionFinished: Boolean,
        introDismissed: Boolean,
        settingsRequested: Boolean,
    ): PomodoroStep = when {
        isLoading -> PomodoroStep.Loading
        isSessionFinished -> PomodoroStep.Complete
        isSessionRunning -> PomodoroStep.Running
        !introDismissed -> PomodoroStep.Intro
        settingsRequested -> PomodoroStep.Settings
        else -> PomodoroStep.Confirm
    }

    /**
     * 시트에서 이미 시작을 누르고 들어온 경로는 소개를 지나친다.
     *
     * 재사용 2탭 계약(`docs/POMODORO_FOCUS_MVP.md` "빠른 시작 계약")을 소개 화면으로 깨면 안 된다.
     */
    fun introDismissedInitially(autoStart: Boolean): Boolean = autoStart

    /** 설정 화면에서 시스템 back 을 받아야 하는가. 아니라면 화면을 떠난다. */
    fun handlesSystemBack(step: PomodoroStep): Boolean = step == PomodoroStep.Settings
}
