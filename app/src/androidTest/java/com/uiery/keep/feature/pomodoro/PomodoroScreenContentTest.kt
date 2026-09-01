package com.uiery.keep.feature.pomodoro

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroSession
import com.uiery.keep.domain.pomodoro.PomodoroSessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * 집중 세션 화면들의 계약을 화면 수준에서 고정한다.
 *
 * 이 기능에서 실기기 확인으로만 잡혔던 결함들(시작 버튼이 화면 밖으로 밀림, 잘린 라벨,
 * 화면이 조용히 다른 모드로 떨어짐)은 전부 도메인 테스트가 잡을 수 없는 종류였다.
 * `docs/POMODORO_FOCUS_MVP.md` 의 계약 중 **화면에서만 확인 가능한 것**을 여기서 잡는다.
 *
 * `BlockScreenContentIntegrationTest` 와 같은 방식으로 content 컴포저블을 직접 렌더링한다 —
 * ViewModel/Hilt 를 세우지 않아 상태를 원하는 지점에 정확히 놓을 수 있다.
 */
class PomodoroScreenContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun runningSession(
        cycle: PomodoroCycle = PomodoroCycle.Focus25,
        phase: PomodoroPhase = PomodoroPhase.Focus,
        cycleIndex: Int = 1,
        completedFocusCount: Int = 0,
        status: PomodoroSessionStatus = PomodoroSessionStatus.Active,
    ) = PomodoroSession(
        cycle = cycle,
        startedAt = Instant.parse("2026-09-01T00:00:00Z"),
        phase = phase,
        cycleIndex = cycleIndex,
        phaseDeadline = Instant.parse("2026-09-01T00:25:00Z"),
        completedFocusCount = completedFocusCount,
        status = status,
    )

    // --- 시작 화면 ---------------------------------------------------------------

    /**
     * 문서의 플로우 계약 1번 — "세션 전체 길이를 반드시 말한다".
     *
     * 사용자가 고르는 것은 "집중 25분"이지만 실제로 예약되는 잠금은 2시간 10분이다.
     * 이 숫자가 시작 버튼과 같은 화면에 없으면 자기가 얼마를 거는지 모르고 시작하게 된다.
     */
    @Test
    fun setupScreenStatesTheWholeLockLengthNotJustTheFocusLength() {
        composeRule.setContent {
            KeepTheme {
                PomodoroSetupContent(
                    state = PomodoroUiState(isLoading = false, selectedAppCount = 3),
                    onOpenSettings = {},
                    onPickApps = {},
                    onStart = {},
                )
            }
        }

        // 25×4 + 5×3 + 15 = 130분 = 2시간 10분
        val total = context.getString(R.string.pomodoro_setup_total_summary_hours, 4, 2, 10)
        composeRule.onNodeWithText(total).assertIsDisplayed()

        // 아래 "없어야 한다" 테스트들이 헛돌지 않는지 같은 헬퍼로 확인한다. 헬퍼가 늘 false 를
        // 반환하면 그 테스트들은 계약이 깨져도 통과한다.
        assertTrue(
            "앱을 골랐으면 시작 버튼이 있어야 한다",
            composeRule.onAllNodesWithTextOrEmpty(context.getString(R.string.pomodoro_setup_start)),
        )
    }

    /**
     * 문서의 플로우 계약 2번 — "막다른 길을 만들지 않는다".
     *
     * 차단 앱이 0개면 시작할 수 없다. 비활성 버튼만 두면 왜 못 누르는지도, 어디서 고치는지도
     * 알 수 없으므로 이유를 말하고 고치러 갈 자리를 준다.
     */
    @Test
    fun setupScreenWithoutAppsExplainsWhyAndOffersTheFix() {
        composeRule.setContent {
            KeepTheme {
                PomodoroSetupContent(
                    state = PomodoroUiState(isLoading = false, selectedAppCount = 0),
                    onOpenSettings = {},
                    onPickApps = {},
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.pomodoro_setup_no_apps))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pomodoro_setup_choose_apps))
            .assertIsDisplayed()
        assertFalse(
            "앱이 0개면 시작 버튼이 없어야 한다",
            composeRule.onAllNodesWithTextOrEmpty(context.getString(R.string.pomodoro_setup_start)),
        )
    }

    /** 요약 카드 자체가 설정으로 가는 자리다. 바꿀 대상이 곧 누를 자리다. */
    @Test
    fun tappingTheSummaryCardOpensSettings() {
        var opened = false
        composeRule.setContent {
            KeepTheme {
                PomodoroSetupContent(
                    state = PomodoroUiState(isLoading = false, selectedAppCount = 1),
                    onOpenSettings = { opened = true },
                    onPickApps = {},
                    onStart = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.pomodoro_cycle_summary_title, 25, 5))
            .performClick()

        assertTrue("요약 카드를 누르면 설정으로 가야 한다", opened)
    }

    // --- 설정 화면 ---------------------------------------------------------------

    /**
     * 문서의 진입 3단 계약 — **설정 화면에는 시작 버튼을 두지 않는다.**
     *
     * 고르는 일과 시작하는 일이 한 화면에 섞이면 무엇을 누르는 자리인지 흐려진다.
     * 이 화면이 하는 일은 고르는 것 하나고, 시작은 돌아가서 한다.
     */
    @Test
    fun settingsScreenHasDoneButNeverStart() {
        composeRule.setContent {
            KeepTheme {
                PomodoroSettingsContent(
                    state = PomodoroUiState(isLoading = false, selectedAppCount = 1),
                    onSelectCycle = {},
                    onSelectCustom = {},
                    onChangeCustomFocus = {},
                    onChangeCustomShortBreak = {},
                    onChangeCustomLongBreak = {},
                    onChangeCustomCycles = {},
                    onToggleBlockDuringBreaks = {},
                    onPickApps = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.pomodoro_settings_done))
            .assertIsDisplayed()
        assertFalse(
            "설정 화면에 시작 버튼이 있으면 고르기와 시작하기가 섞인다",
            composeRule.onAllNodesWithTextOrEmpty(context.getString(R.string.pomodoro_setup_start)),
        )
    }

    /** 사이클을 바꾸는 것은 총 잠금을 2시간 10분에서 4시간 10분으로 바꾸는 결정이다. */
    @Test
    fun settingsScreenSummaryFollowsTheSelectedCycle() {
        composeRule.setContent {
            KeepTheme {
                PomodoroSettingsContent(
                    state = PomodoroUiState(
                        isLoading = false,
                        selectedAppCount = 1,
                        selectedCycle = PomodoroCycle.Focus50,
                    ),
                    onSelectCycle = {},
                    onSelectCustom = {},
                    onChangeCustomFocus = {},
                    onChangeCustomShortBreak = {},
                    onChangeCustomLongBreak = {},
                    onChangeCustomCycles = {},
                    onToggleBlockDuringBreaks = {},
                    onPickApps = {},
                    onDone = {},
                )
            }
        }

        // 50×4 + 10×3 + 20 = 250분 = 4시간 10분
        composeRule
            .onNodeWithText(context.getString(R.string.pomodoro_setup_total_summary_hours, 4, 4, 10))
            .assertIsDisplayed()
    }

    // --- 소개 화면 ---------------------------------------------------------------

    /** 소개는 설명을 얹는 자리가 아니라 계속할지 고르는 자리다. CTA 가 다음 단계로 넘긴다. */
    @Test
    fun introScreenAdvancesOnItsCta() {
        var advanced = false
        composeRule.setContent {
            KeepTheme {
                PomodoroIntroContent(cycle = PomodoroCycle.Focus25, onNext = { advanced = true })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.pomodoro_intro_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pomodoro_intro_cta)).performClick()

        assertTrue("소개 CTA 는 다음 단계로 넘어가야 한다", advanced)
    }

    // --- 진행 화면 ---------------------------------------------------------------

    /**
     * 남은 시간과 현재 구간이 **색이 아니라 글자로** 전달되어야 한다.
     * 휴식도 세션의 일부이며 차단이 유지되는 구간이라는 것을 화면이 직접 말한다.
     */
    @Test
    fun runningScreenNamesTheFocusPhaseInText() {
        assertPhaseLabel(PomodoroPhase.Focus, R.string.pomodoro_phase_focus)
    }

    /** 휴식도 세션의 일부이며 차단이 유지되는 구간이다. 화면이 그 사실을 직접 말한다. */
    @Test
    fun runningScreenNamesTheShortBreakPhaseInText() {
        assertPhaseLabel(PomodoroPhase.ShortBreak, R.string.pomodoro_phase_short_break)
    }

    @Test
    fun runningScreenNamesTheLongBreakPhaseInText() {
        assertPhaseLabel(PomodoroPhase.LongBreak, R.string.pomodoro_phase_long_break)
    }

    /** `setContent` 는 rule 당 한 번만 부를 수 있어 구간마다 테스트를 나눈다. */
    private fun assertPhaseLabel(phase: PomodoroPhase, labelRes: Int) {
        composeRule.setContent {
            KeepTheme {
                PomodoroRunningContent(
                    state = PomodoroUiState(
                        isLoading = false,
                        selectedAppCount = 2,
                        session = runningSession(phase = phase),
                        remainingSeconds = 300,
                    ),
                    onEnd = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(labelRes)).assertIsDisplayed()
    }

    // --- 완료 화면 ---------------------------------------------------------------

    /**
     * 아무것도 못 한 세션에 "완료한 0회는 기록에 남아요"라고 말하면 문장이 성립하지 않고
     * 못 했다는 사실만 남는다. 실기기에서 잡혔던 결함이라 여기서 고정한다.
     */
    @Test
    fun completeScreenDoesNotReportZeroFocusAsARecord() {
        composeRule.setContent {
            KeepTheme {
                PomodoroCompleteContent(
                    state = PomodoroUiState(
                        isLoading = false,
                        session = runningSession(
                            completedFocusCount = 0,
                            status = PomodoroSessionStatus.EndedEarly,
                        ),
                    ),
                    onRestart = {},
                    onLeave = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.pomodoro_ended_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pomodoro_complete_focus_count_label))
            .assertIsDisplayed()
    }
}

/** 해당 문구를 가진 노드가 하나라도 있는지. 없어야 한다는 계약을 표현하려고 둔다. */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextOrEmpty(
    text: String,
): Boolean = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
