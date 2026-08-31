package com.uiery.keep.domain.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * `docs/POMODORO_FOCUS_MVP.md`의 "차단 판단 정책"을 고정한다.
 *
 * `Focus25` 기준 한 세션의 시간표(시작 T):
 * 집중1 `T+25` · 짧은휴식 `T+30` · 집중2 `T+55` · 짧은휴식 `T+60` ·
 * 집중3 `T+85` · 짧은휴식 `T+90` · 집중4 `T+115` · 긴휴식 `T+130` → 완료
 */
class PomodoroPolicyTest {

    private val start: Instant = Instant.parse("2026-08-27T09:00:00Z")

    private fun at(minutes: Long): Instant = start.plus(Duration.ofMinutes(minutes))

    private fun session(now: Instant = start): PomodoroSession =
        PomodoroPolicy.start(cycle = PomodoroCycle.Focus25, now = now)

    @Test
    fun startBeginsOnFirstFocusWithNothingCompletedYet() {
        val session = session()

        assertEquals(PomodoroPhase.Focus, session.phase)
        assertEquals(1, session.cycleIndex)
        assertEquals(0, session.completedFocusCount)
        assertEquals(PomodoroSessionStatus.Active, session.status)
        assertEquals(at(25), session.phaseDeadline)
    }

    @Test
    fun focusPhaseBlocksSelectedApps() {
        assertTrue(PomodoroPolicy.isActive(session = session(), now = at(10)))
    }

    /**
     * 이 기능의 핵심 계약이다. 휴식은 "차단이 풀린 시간"이 아니라 세션의 일부다.
     * 이 테스트가 깨지면 기능이 아니라 계약이 깨진 것이다.
     */
    @Test
    fun breakPhaseKeepsBlockingExactlyLikeFocus() {
        val duringShortBreak = PomodoroPolicy.resolve(session = session(), now = at(27))
        assertEquals(PomodoroPhase.ShortBreak, duringShortBreak.phase)
        assertTrue(PomodoroPolicy.isActive(session = duringShortBreak, now = at(27)))

        val duringLongBreak = PomodoroPolicy.resolve(session = session(), now = at(120))
        assertEquals(PomodoroPhase.LongBreak, duringLongBreak.phase)
        assertTrue(PomodoroPolicy.isActive(session = duringLongBreak, now = at(120)))
    }

    @Test
    fun focusTurnsIntoShortBreakAtItsDeadline() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(25))

        assertEquals(PomodoroPhase.ShortBreak, resolved.phase)
        assertEquals(1, resolved.completedFocusCount)
        assertEquals(1, resolved.cycleIndex)
        assertEquals(at(30), resolved.phaseDeadline)
    }

    /**
     * 따라잡기는 저장된 deadline에서 이어 붙인다. `now`에서 다시 재면 자리를 비운 시간만큼
     * 세션이 늘어나 사용자가 예약하지 않은 잠금이 된다.
     */
    @Test
    fun catchUpChainsFromStoredDeadlineNotFromNow() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(29))

        assertEquals(PomodoroPhase.ShortBreak, resolved.phase)
        assertEquals(at(30), resolved.phaseDeadline)
    }

    @Test
    fun catchUpConsumesEveryMissedPhaseButCountsOnlyFocus() {
        // 집중1·짧은휴식·집중2를 건너뛰고 두 번째 짧은 휴식 한가운데에서 돌아왔다.
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(58))

        assertEquals(PomodoroPhase.ShortBreak, resolved.phase)
        assertEquals(2, resolved.completedFocusCount)
        assertEquals(2, resolved.cycleIndex)
        assertEquals(at(60), resolved.phaseDeadline)
        assertEquals(PomodoroSessionStatus.Active, resolved.status)
    }

    @Test
    fun lastFocusIsFollowedByLongBreakInsteadOfShortOne() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(115))

        assertEquals(PomodoroPhase.LongBreak, resolved.phase)
        assertEquals(PomodoroCycle.DEFAULT_CYCLES, resolved.completedFocusCount)
        assertEquals(at(130), resolved.phaseDeadline)
    }

    @Test
    fun sessionCompletesWhenTheLongBreakEnds() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(130))

        assertEquals(PomodoroSessionStatus.Completed, resolved.status)
        assertEquals(PomodoroCycle.DEFAULT_CYCLES, resolved.completedFocusCount)
    }

    @Test
    fun completedSessionStopsBlocking() {
        val completed = PomodoroPolicy.resolve(session = session(), now = at(130))

        assertFalse(PomodoroPolicy.isActive(session = completed, now = at(131)))
    }

    /**
     * 며칠 뒤에 앱을 열어도 세션이 그 시간만큼 굴러가지 않는다. 전환 횟수가 유한하므로
     * 세션은 자기 시간표의 끝에서 멈춘다.
     */
    @Test
    fun longAbsenceCompletesTheSessionAndDoesNotRunPastIt() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(60 * 24 * 3))

        assertEquals(PomodoroSessionStatus.Completed, resolved.status)
        assertEquals(PomodoroCycle.DEFAULT_CYCLES, resolved.completedFocusCount)
        assertEquals(at(130), resolved.phaseDeadline)
    }

    @Test
    fun endEarlyStopsBlockingButKeepsWhatWasAlreadyCompleted() {
        val duringThirdFocus = PomodoroPolicy.resolve(session = session(), now = at(70))
        assertEquals(2, duringThirdFocus.completedFocusCount)

        val ended = PomodoroPolicy.endEarly(duringThirdFocus)

        assertEquals(PomodoroSessionStatus.EndedEarly, ended.status)
        assertEquals(2, ended.completedFocusCount)
        assertFalse(PomodoroPolicy.isActive(session = ended, now = at(71)))
    }

    @Test
    fun finishedSessionIsNeverAdvancedAgain() {
        val ended = PomodoroPolicy.endEarly(session())

        assertEquals(ended, PomodoroPolicy.resolve(session = ended, now = at(130)))
    }

    @Test
    fun remainingCountsDownWithinThePhaseAndIsZeroOnceFinished() {
        assertEquals(Duration.ofMinutes(15), PomodoroPolicy.remaining(session = session(), now = at(10)))

        val ended = PomodoroPolicy.endEarly(session())
        assertEquals(Duration.ZERO, PomodoroPolicy.remaining(session = ended, now = at(10)))
    }

    @Test
    fun remainingNeverGoesNegativeWhenTheDeadlineHasPassed() {
        assertEquals(Duration.ZERO, PomodoroPolicy.remaining(session = session(), now = at(40)))
    }

    @Test
    fun nextFocusCycleIsAnnouncedOnlyDuringAShortBreak() {
        val duringFocus = PomodoroPolicy.resolve(session = session(), now = at(10))
        assertNull(PomodoroPolicy.nextFocusCycleIndex(duringFocus))

        val duringShortBreak = PomodoroPolicy.resolve(session = session(), now = at(27))
        assertEquals(2, PomodoroPolicy.nextFocusCycleIndex(duringShortBreak))

        // 긴 휴식 뒤에는 이 세션의 집중이 더 없다.
        val duringLongBreak = PomodoroPolicy.resolve(session = session(), now = at(120))
        assertNull(PomodoroPolicy.nextFocusCycleIndex(duringLongBreak))
    }

    @Test
    fun completedFocusDurationCountsFocusPhasesOnly() {
        val completed = PomodoroPolicy.resolve(session = session(), now = at(130))

        // 세션 전체는 130분이지만 집중한 시간은 100분이다. 휴식을 성과로 세지 않는다.
        assertEquals(Duration.ofMinutes(100), PomodoroPolicy.completedFocusDuration(completed))
    }

    @Test
    fun phaseDurationFollowsThePresetForEveryPhase() {
        val cycle = PomodoroCycle.Focus50
        val focus = PomodoroPolicy.start(cycle = cycle, now = start)
        assertEquals(Duration.ofMinutes(50), PomodoroPolicy.phaseDuration(focus))

        val shortBreak = PomodoroPolicy.resolve(session = focus, now = start.plus(Duration.ofMinutes(50)))
        assertEquals(PomodoroPhase.ShortBreak, shortBreak.phase)
        assertEquals(Duration.ofMinutes(10), PomodoroPolicy.phaseDuration(shortBreak))
    }

    /**
     * 사용자가 고르는 것은 "집중 25분"이지만 실제로 예약되는 잠금은 2시간 10분이다.
     * 이 값이 화면에 나오지 않으면 사용자는 자기가 얼마를 거는지 모르고 시작한다.
     */
    @Test
    fun totalDurationCoversEveryFocusAndBreakInTheSession() {
        // 집중 4×25 + 짧은 휴식 3×5 + 긴 휴식 15 = 130분
        assertEquals(Duration.ofMinutes(130), PomodoroPolicy.totalDuration(PomodoroCycle.Focus25))
        // 집중 4×50 + 짧은 휴식 3×10 + 긴 휴식 20 = 250분 (4시간 10분)
        assertEquals(Duration.ofMinutes(250), PomodoroPolicy.totalDuration(PomodoroCycle.Focus50))
    }

    @Test
    fun totalDurationFollowsACustomCycle() {
        val cycle = PomodoroCycle.custom(
            focusMinutes = 40,
            shortBreakMinutes = 7,
            longBreakMinutes = 25,
        )!!

        // 4×40 + 3×7 + 25 = 206분
        assertEquals(Duration.ofMinutes(206), PomodoroPolicy.totalDuration(cycle))
    }

    /** 세션이 실제로 그 시간만큼 도는지 — 총 길이는 시간표의 끝과 같아야 한다. */
    @Test
    fun totalDurationMatchesWhenTheSessionActuallyEnds() {
        val session = session()

        assertTrue(PomodoroPolicy.isActive(session = session, now = at(129)))
        assertFalse(PomodoroPolicy.isActive(session = session, now = at(130)))
        assertEquals(
            Duration.ofMinutes(130),
            PomodoroPolicy.totalDuration(PomodoroCycle.Focus25),
        )
    }

    /**
     * 휴식 차단을 끄면 휴식 구간에서만 물러난다. 세션 자체는 계속 살아 있다 —
     * 잠금이 누구 것인지 판단하는 쪽이 그 구분에 기댄다.
     */
    @Test
    fun blocksNowStepsAsideOnBreaksOnlyWhenBreakBlockingIsOff() {
        val duringBreak = PomodoroPolicy.resolve(session = session(), now = at(27))

        assertTrue(PomodoroPolicy.blocksNow(duringBreak, at(27), blockDuringBreaks = true))
        assertFalse(PomodoroPolicy.blocksNow(duringBreak, at(27), blockDuringBreaks = false))
        // 물러나도 세션은 살아 있다.
        assertTrue(PomodoroPolicy.isActive(duringBreak, at(27)))
    }

    @Test
    fun blocksNowAlwaysBlocksDuringFocusRegardlessOfTheSetting() {
        val duringFocus = session()

        assertTrue(PomodoroPolicy.blocksNow(duringFocus, at(10), blockDuringBreaks = true))
        assertTrue(PomodoroPolicy.blocksNow(duringFocus, at(10), blockDuringBreaks = false))
    }

    @Test
    fun aFinishedSessionNeverBlocksUnderEitherSetting() {
        val completed = PomodoroPolicy.resolve(session = session(), now = at(130))

        assertFalse(PomodoroPolicy.blocksNow(completed, at(131), blockDuringBreaks = true))
        assertFalse(PomodoroPolicy.blocksNow(completed, at(131), blockDuringBreaks = false))
    }

    /**
     * 4회 고정이던 시절에는 `25/5` 를 고르면 무조건 2시간 10분이었다. 반복 횟수가 총 잠금 시간을
     * 가장 크게 흔든다.
     */
    @Test
    fun cycleCountDrivesTheWholeSessionSchedule() {
        val twice = PomodoroCycle.custom(
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            cycles = 2,
        )!!
        // 25×2 + 5×1 + 15 = 70분
        assertEquals(Duration.ofMinutes(70), PomodoroPolicy.totalDuration(twice))

        val session = PomodoroPolicy.start(cycle = twice, now = start)
        val afterSecondFocus = PomodoroPolicy.resolve(session = session, now = at(55))
        // 두 번째 집중이 마지막이므로 짧은 휴식이 아니라 긴 휴식으로 넘어간다.
        assertEquals(PomodoroPhase.LongBreak, afterSecondFocus.phase)
        assertEquals(2, afterSecondFocus.completedFocusCount)

        val completed = PomodoroPolicy.resolve(session = session, now = at(70))
        assertEquals(PomodoroSessionStatus.Completed, completed.status)
    }

    /** 집중 1회는 사이클이 아니다 — 반복 없는 한 번의 잠금은 타이머 세그먼트가 하는 일이다. */
    @Test
    fun cycleCountRefusesValuesOutsideTheAllowedRange() {
        assertNull(PomodoroCycle.custom(25, 5, 15, cycles = 1))
        assertNull(PomodoroCycle.custom(25, 5, 15, cycles = 9))
        assertNotNull(PomodoroCycle.custom(25, 5, 15, cycles = 2))
        assertNotNull(PomodoroCycle.custom(25, 5, 15, cycles = 8))
    }

    /** 따라잡기 루프의 상한도 횟수를 따라가야 한다. 8회 세션이 4회에서 멈추면 안 된다. */
    @Test
    fun catchUpCoversEveryCycleOfALongerSession() {
        val eight = PomodoroCycle.custom(25, 5, 15, cycles = 8)!!
        val session = PomodoroPolicy.start(cycle = eight, now = start)

        // 25×8 + 5×7 + 15 = 250분
        assertEquals(Duration.ofMinutes(250), PomodoroPolicy.totalDuration(eight))
        val completed = PomodoroPolicy.resolve(session = session, now = at(250))
        assertEquals(PomodoroSessionStatus.Completed, completed.status)
        assertEquals(8, completed.completedFocusCount)
    }

    @Test
    fun nullSessionNeverBlocks() {
        assertFalse(PomodoroPolicy.isActive(session = null, now = start))
    }

    @Test
    fun presetAnalyticsKeysRoundTripAndAreStable() {
        assertEquals(PomodoroPresetKind.Focus25, PomodoroPresetKind.fromAnalyticsKey("25_5"))
        assertEquals(PomodoroPresetKind.Focus50, PomodoroPresetKind.fromAnalyticsKey("50_10"))
        assertEquals(PomodoroPresetKind.Custom, PomodoroPresetKind.fromAnalyticsKey("custom"))
        assertNull(PomodoroPresetKind.fromAnalyticsKey("30_5"))
        assertNull(PomodoroPresetKind.fromAnalyticsKey(null))
    }

    @Test
    fun customCycleDrivesEveryPhaseLengthJustLikeAPreset() {
        val cycle = PomodoroCycle.custom(
            focusMinutes = 40,
            shortBreakMinutes = 7,
            longBreakMinutes = 25,
        )!!
        val session = PomodoroPolicy.start(cycle = cycle, now = start)

        assertEquals(PomodoroPresetKind.Custom, session.cycle.kind)
        assertEquals(at(40), session.phaseDeadline)

        val shortBreak = PomodoroPolicy.resolve(session = session, now = at(40))
        assertEquals(PomodoroPhase.ShortBreak, shortBreak.phase)
        assertEquals(at(47), shortBreak.phaseDeadline)
        assertEquals(Duration.ofMinutes(7), PomodoroPolicy.phaseDuration(shortBreak))
    }

    /**
     * 상한이 없으면 집중 세션이 목표 잠금 흉내를 낼 수 있고, 하한이 없으면 1분짜리 세션으로
     * 완주 지표를 채울 수 있다.
     */
    @Test
    fun customCycleRefusesLengthsOutsideTheAllowedRange() {
        assertNull(PomodoroCycle.custom(focusMinutes = 4, shortBreakMinutes = 5, longBreakMinutes = 15))
        assertNull(PomodoroCycle.custom(focusMinutes = 121, shortBreakMinutes = 5, longBreakMinutes = 15))
        assertNull(PomodoroCycle.custom(focusMinutes = 25, shortBreakMinutes = 0, longBreakMinutes = 15))
        assertNull(PomodoroCycle.custom(focusMinutes = 25, shortBreakMinutes = 61, longBreakMinutes = 15))
        assertNull(PomodoroCycle.custom(focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 0))
        assertNull(PomodoroCycle.custom(focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 121))
    }

    @Test
    fun customCycleAcceptsTheRangeBoundaries() {
        assertNotNull(PomodoroCycle.custom(focusMinutes = 5, shortBreakMinutes = 1, longBreakMinutes = 1))
        assertNotNull(PomodoroCycle.custom(focusMinutes = 120, shortBreakMinutes = 60, longBreakMinutes = 120))
    }

    /**
     * 프리셋 정의가 나중에 바뀌어도 이미 돌던 세션의 길이는 저장된 값 그대로여야 한다.
     * 그래서 복원은 표를 다시 찾지 않고 저장된 분 값을 그대로 쓴다.
     */
    @Test
    fun restoreKeepsTheStoredKindEvenWhenLengthsDoNotMatchThePresetTable() {
        val restored = PomodoroCycle.restore(
            kind = PomodoroPresetKind.Focus25,
            focusMinutes = 30,
            shortBreakMinutes = 6,
            longBreakMinutes = 18,
        )!!

        assertEquals(PomodoroPresetKind.Focus25, restored.kind)
        assertEquals(Duration.ofMinutes(30), restored.focus)
    }

    @Test
    fun breakTypeAnalyticsKeyExistsOnlyForBreaks() {
        assertNull(PomodoroPhase.Focus.breakTypeAnalyticsKey)
        assertEquals("short", PomodoroPhase.ShortBreak.breakTypeAnalyticsKey)
        assertEquals("long", PomodoroPhase.LongBreak.breakTypeAnalyticsKey)
    }
}
