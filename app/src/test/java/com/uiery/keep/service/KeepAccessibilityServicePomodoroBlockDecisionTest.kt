package com.uiery.keep.service

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import com.uiery.keep.domain.pomodoro.PomodoroSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * `docs/POMODORO_FOCUS_MVP.md` 의 "차단 판단 정책" 계약을 실제 차단 판정 위에서 고정한다.
 *
 * 정책 단위 테스트는 `PomodoroPolicyTest` 에 있다. 여기서는 세션이 기존 잠금들과 한 화면에서
 * 만났을 때 무엇이 이기고 무엇이 남는지를 본다.
 */
class KeepAccessibilityServicePomodoroBlockDecisionTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val sessionStart: LocalDateTime = LocalDateTime.of(2026, 8, 27, 9, 0)
    private val blockedPackage = "com.video.app"

    private fun at(minutes: Long): LocalDateTime = sessionStart.plusMinutes(minutes)

    private fun session(
        now: LocalDateTime = sessionStart,
        cycle: PomodoroCycle = PomodoroCycle.Focus25,
    ): PomodoroSession = PomodoroPolicy.start(
        cycle = cycle,
        now = now.atZone(zone).toInstant(),
    )

    private fun resolve(
        now: LocalDateTime,
        pomodoroSession: PomodoroSession?,
        packageName: String = blockedPackage,
        selectedAppPackages: Set<String> = setOf(blockedPackage),
        isKeep: Boolean = false,
        cachedGoalLocks: List<GoalLock> = emptyList(),
        parentModeSession: ParentModeSession? = null,
        isEmergencyUnlocked: Boolean = false,
        blockDuringBreaks: Boolean = true,
        lockTime: String? = null,
    ) = resolveForegroundBlockRequest(
        packageName = packageName,
        prefs = AccessibilityBlockingPreferences(
            isKeep = isKeep,
            lockTime = lockTime,
            selectedAppPackages = selectedAppPackages,
        ),
        cachedRoutines = emptyList(),
        cachedGoalLocks = cachedGoalLocks,
        parentModeSession = parentModeSession,
        pomodoroSession = pomodoroSession,
        pomodoroBlockDuringBreaks = blockDuringBreaks,
        now = now,
        isEmergencyUnlocked = isEmergencyUnlocked,
        isDuplicateBlock = false,
    )

    @Test
    fun focusPhaseBlocksSelectedAppWithItsOwnSource() {
        val request = resolve(now = at(10), pomodoroSession = session())

        assertEquals(
            ForegroundBlockRequest(
                packageName = blockedPackage,
                blockSource = AnalyticsBlockSource.POMODORO,
            ),
            request,
        )
    }

    /**
     * 이 기능의 핵심 계약이다. 휴식은 차단이 풀리는 시간이 아니다.
     * 이 테스트가 깨지면 사용자가 5분마다 빠져나갈 구멍이 생긴 것이다.
     */
    @Test
    fun shortBreakBlocksExactlyLikeFocus() {
        val request = resolve(now = at(27), pomodoroSession = session())

        assertNotNull("휴식 중에도 차단되어야 한다", request)
        assertEquals(AnalyticsBlockSource.POMODORO, request?.blockSource)
    }

    @Test
    fun longBreakBlocksExactlyLikeFocus() {
        val request = resolve(now = at(120), pomodoroSession = session())

        assertNotNull("긴 휴식 중에도 차단되어야 한다", request)
        assertEquals(AnalyticsBlockSource.POMODORO, request?.blockSource)
    }

    /**
     * 뽀모도로 카테고리의 기본 동작은 "휴식엔 열림"이다. 끄면 휴식 구간에 물러난다.
     */
    @Test
    fun turningOffBreakBlockingOpensAppsDuringABreak() {
        assertNull(
            resolve(now = at(27), pomodoroSession = session(), blockDuringBreaks = false),
        )
    }

    @Test
    fun turningOffBreakBlockingStillBlocksDuringFocus() {
        val request = resolve(now = at(10), pomodoroSession = session(), blockDuringBreaks = false)

        assertEquals(AnalyticsBlockSource.POMODORO, request?.blockSource)
    }

    /**
     * 세션은 사이클 전체 길이의 타이머 잠금 하나를 만든다. 휴식 차단을 껐을 때 세션만 물러나고
     * 그 잠금이 남으면, 결국 `timed_lock` 으로 계속 막혀서 설정이 아무 일도 하지 않는다.
     */
    @Test
    fun theSessionsOwnTimedLockAlsoStepsAsideDuringABreak() {
        val sessionDeadline = at(130).atZone(zone).toInstant().toString()

        assertNull(
            resolve(
                now = at(27),
                pomodoroSession = session(),
                blockDuringBreaks = false,
                lockTime = sessionDeadline,
            ),
        )
    }

    /**
     * 물러나는 것은 세션이 만든 잠금뿐이다. 루틴처럼 세션과 무관한 약속은 휴식에도 그대로 막는다.
     */
    @Test
    fun aGoalLockKeepsBlockingThroughABreakEvenWhenBreakBlockingIsOff() {
        val goalLock = GoalLock(
            id = 1L,
            goalName = "시험 준비",
            startDate = LocalDate.of(2026, 8, 20),
            endDate = LocalDate.of(2026, 9, 20),
            lockMode = GoalLockMode.AllDay,
            selectedPackages = setOf(blockedPackage),
            status = GoalLockStoredStatus.Active,
        )

        val request = resolve(
            now = at(27),
            pomodoroSession = session(),
            blockDuringBreaks = false,
            cachedGoalLocks = listOf(goalLock),
        )

        assertEquals(AnalyticsBlockSource.GOAL_LOCK, request?.blockSource)
    }

    /** 즉시 차단은 사용자가 따로 켠 것이므로 휴식이 열지 않는다. */
    @Test
    fun manualKeepKeepsBlockingThroughABreakEvenWhenBreakBlockingIsOff() {
        val request = resolve(
            now = at(27),
            pomodoroSession = session(),
            blockDuringBreaks = false,
            isKeep = true,
        )

        assertEquals(AnalyticsBlockSource.MANUAL_KEEP, request?.blockSource)
    }

    @Test
    fun completedSessionStopsBlocking() {
        assertNull(resolve(now = at(130), pomodoroSession = session()))
    }

    @Test
    fun sessionEndedByTheUserStopsBlocking() {
        val ended = PomodoroPolicy.endEarly(session())

        assertNull(resolve(now = at(10), pomodoroSession = ended))
    }

    @Test
    fun sessionOnlyBlocksAppsTheUserSelected() {
        assertNull(
            resolve(
                now = at(10),
                pomodoroSession = session(),
                packageName = "com.unselected.app",
            ),
        )
    }

    @Test
    fun noSessionMeansNoPomodoroBlock() {
        assertNull(resolve(now = at(10), pomodoroSession = null))
    }

    /**
     * 세션은 기존 약속 위에 얹히는 것이지 그 출처를 가져가지 않는다. 목표 잠금 차단이 `pomodoro`
     * 로 기록되면 세션이 끝났을 때 그 차단도 끝난 것처럼 읽힌다.
     */
    @Test
    fun goalLockKeepsItsSourceWhileASessionIsRunning() {
        val goalLock = GoalLock(
            id = 1L,
            goalName = "시험 준비",
            startDate = LocalDate.of(2026, 8, 20),
            endDate = LocalDate.of(2026, 9, 20),
            lockMode = GoalLockMode.AllDay,
            selectedPackages = setOf(blockedPackage),
            status = GoalLockStoredStatus.Active,
        )

        val request = resolve(
            now = at(10),
            pomodoroSession = session(),
            cachedGoalLocks = listOf(goalLock),
        )

        assertEquals(AnalyticsBlockSource.GOAL_LOCK, request?.blockSource)
        assertEquals("1", request?.goalLockId)
    }

    @Test
    fun goalLockStillBlocksAfterTheSessionIsOver() {
        val goalLock = GoalLock(
            id = 1L,
            goalName = "시험 준비",
            startDate = LocalDate.of(2026, 8, 20),
            endDate = LocalDate.of(2026, 9, 20),
            lockMode = GoalLockMode.AllDay,
            selectedPackages = setOf(blockedPackage),
            status = GoalLockStoredStatus.Active,
        )

        val request = resolve(
            now = at(130),
            pomodoroSession = session(),
            cachedGoalLocks = listOf(goalLock),
        )

        assertEquals(AnalyticsBlockSource.GOAL_LOCK, request?.blockSource)
    }

    @Test
    fun parentModeOutranksTheSession() {
        val parentModeSession = ParentModeSession(
            startedAtMillis = at(0).atZone(zone).toInstant().toEpochMilli(),
            expiresAtMillis = at(60).atZone(zone).toInstant().toEpochMilli(),
            durationMinutes = 60,
            allowedApps = emptySet(),
            state = ParentModeSessionState.Active,
        )

        val request = resolve(
            now = at(10),
            pomodoroSession = session(),
            parentModeSession = parentModeSession,
        )

        assertEquals(AnalyticsBlockSource.PARENT_MODE, request?.blockSource)
    }

    /**
     * 뽀모도로는 사용자가 자기 자신에게 건 잠금이므로 긴급 해제가 열 수 있다.
     * 부모 모드를 막은 #1177 과는 반대 방향이다.
     */
    @Test
    fun emergencyUnlockOpensTheSessionBecauseItIsASelfControlLock() {
        assertNull(
            resolve(
                now = at(10),
                pomodoroSession = session(),
                isEmergencyUnlocked = true,
            ),
        )
    }

    /**
     * 앱이 죽어 있는 동안 지나간 구간은 저장된 deadline 기준으로 소비된다. 자리를 비운 시간이
     * 세션을 늘려서는 안 된다.
     */
    @Test
    fun blockingFollowsWallClockAcrossAProcessDeath() {
        // 저장된 세션은 여전히 첫 집중 구간이지만, 실제로는 세션 시간표의 끝을 지났다.
        val stale = session()

        assertNotNull(resolve(now = at(100), pomodoroSession = stale))
        assertNull(resolve(now = at(131), pomodoroSession = stale))
    }

    @Test
    fun longerPresetKeepsBlockingPastTheShorterPresetsEnd() {
        val long = session(cycle = PomodoroCycle.Focus50)

        // Focus25 세션이라면 130분에 끝났을 시점이지만 Focus50 세션은 260분짜리다.
        assertNotNull(resolve(now = at(131), pomodoroSession = long))
        assertNull(resolve(now = at(261), pomodoroSession = long))
    }

    @Test
    fun manualKeepStillWinsAttributionWhenBothAreOn() {
        // 세션 없이 즉시 차단만 켜져 있으면 기존 출처가 그대로 유지된다.
        val request = resolve(now = at(10), pomodoroSession = null, isKeep = true)

        assertEquals(AnalyticsBlockSource.MANUAL_KEEP, request?.blockSource)
    }

    @Test
    fun sessionSourceWinsOverManualKeepWhileBothAreActive() {
        // 즉시 차단과 세션이 동시에 켜져 있으면 지금 사용자가 하고 있는 일이 세션이다.
        val request = resolve(now = at(10), pomodoroSession = session(), isKeep = true)

        assertEquals(AnalyticsBlockSource.POMODORO, request?.blockSource)
    }

    @Test
    fun phaseAtGivenMomentMatchesWhatTheBlockScreenWouldShow() {
        val resolved = PomodoroPolicy.resolve(
            session = session(),
            now = at(27).atZone(zone).toInstant(),
        )

        assertEquals(PomodoroPhase.ShortBreak, resolved.phase)
        assertEquals(1, resolved.completedFocusCount)
        assertEquals(Duration.ofMinutes(3), PomodoroPolicy.remaining(resolved, at(27).atZone(zone).toInstant()))
    }
}
