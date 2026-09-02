package com.uiery.keep.feature.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 진입 3단과 세션 상태의 우선순위를 고정한다.
 *
 * 이 판단은 컴포저블 안의 `var` 로만 있어서 화면을 띄우지 않고는 확인할 수 없었다. 실기기
 * 검증에서 잡혔던 결함 중 둘이 정확히 이 규칙이 틀려서 생긴 것이었다 — 스플래시가 세션 중에
 * 잠금 화면으로 보내 세션을 끝낼 수 없었던 것, 그리고 설정을 열어 둔 채 세션이 시작되던 것.
 */
class PomodoroStepPolicyTest {

    private fun resolve(
        isLoading: Boolean = false,
        isSessionRunning: Boolean = false,
        isSessionFinished: Boolean = false,
        introDismissed: Boolean = true,
        settingsRequested: Boolean = false,
    ) = PomodoroStepPolicy.resolve(
        isLoading = isLoading,
        isSessionRunning = isSessionRunning,
        isSessionFinished = isSessionFinished,
        introDismissed = introDismissed,
        settingsRequested = settingsRequested,
    )

    @Test
    fun menuEntryStartsAtTheIntro() {
        assertEquals(PomodoroStep.Intro, resolve(introDismissed = false))
    }

    @Test
    fun dismissingTheIntroLandsOnConfirmNotSettings() {
        assertEquals(PomodoroStep.Confirm, resolve(introDismissed = true))
    }

    @Test
    fun confirmScreenOpensSettingsOnRequest() {
        assertEquals(PomodoroStep.Settings, resolve(settingsRequested = true))
    }

    /**
     * 빠른 시작(재사용 2탭)은 소개를 지나친다. 시트에서 이미 시작을 눌렀는데 소개를 한 번 더
     * 보여주면 2탭 계약이 깨진다.
     */
    @Test
    fun autoStartSkipsTheIntro() {
        assertTrue(PomodoroStepPolicy.introDismissedInitially(autoStart = true))
        assertFalse(PomodoroStepPolicy.introDismissedInitially(autoStart = false))
    }

    /** 세션 상태가 화면 안의 선택보다 먼저다. 설정을 열어 둔 채로 세션이 시작될 수 있다. */
    @Test
    fun aRunningSessionOverridesEveryEntryStep() {
        listOf(
            resolve(isSessionRunning = true, introDismissed = false),
            resolve(isSessionRunning = true, settingsRequested = true),
        ).forEach { assertEquals(PomodoroStep.Running, it) }
    }

    @Test
    fun aFinishedSessionOverridesEveryEntryStep() {
        listOf(
            resolve(isSessionFinished = true, introDismissed = false),
            resolve(isSessionFinished = true, settingsRequested = true),
        ).forEach { assertEquals(PomodoroStep.Complete, it) }
    }

    /** 끝난 세션과 도는 세션이 함께 참일 수 없지만, 완료가 이긴다는 순서를 고정해 둔다. */
    @Test
    fun finishedWinsOverRunning() {
        assertEquals(
            PomodoroStep.Complete,
            resolve(isSessionRunning = true, isSessionFinished = true),
        )
    }

    @Test
    fun loadingShowsNothingRatherThanAWrongStep() {
        assertEquals(
            PomodoroStep.Loading,
            resolve(isLoading = true, introDismissed = false, settingsRequested = true),
        )
    }

    /** 설정에서만 시스템 back 을 가로챈다. 나머지 단계에서는 화면을 떠나야 한다. */
    @Test
    fun onlySettingsInterceptsSystemBack() {
        assertTrue(PomodoroStepPolicy.handlesSystemBack(PomodoroStep.Settings))
        listOf(
            PomodoroStep.Intro,
            PomodoroStep.Confirm,
            PomodoroStep.Running,
            PomodoroStep.Complete,
            PomodoroStep.Loading,
        ).forEach { assertFalse(it.name, PomodoroStepPolicy.handlesSystemBack(it)) }
    }
}
