package com.uiery.keep.notification

import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PomodoroNotificationContentTest {

    private val start: Instant = Instant.parse("2026-08-27T09:00:00Z")

    private fun at(minutes: Long): Instant = start.plus(Duration.ofMinutes(minutes))

    private fun session(now: Instant = start) =
        PomodoroPolicy.start(cycle = PomodoroCycle.Focus25, now = now)

    @Test
    fun remainingIsRenderedAsAMinuteSecondClock() {
        val content = PomodoroNotificationContent.from(
            session = session(),
            now = start.plus(Duration.ofSeconds(498)),
            previousPhase = PomodoroPhase.Focus,
        )

        // 25분 중 8분 18초가 지났으므로 16:42 가 남는다.
        assertEquals("16:42", content.remainingClock)
        assertEquals(PomodoroPhase.Focus, content.phase)
        assertEquals(1, content.cycleIndex)
        assertEquals(PomodoroCycle.DEFAULT_CYCLES, content.cyclesPerSession)
    }

    /**
     * 초마다 알리면 25분 동안 폰이 계속 울린다. 소리는 구간이 바뀌는 순간에만 난다.
     */
    @Test
    fun aTickWithinTheSamePhaseDoesNotAlert() {
        val content = PomodoroNotificationContent.from(
            session = session(),
            now = at(10),
            previousPhase = PomodoroPhase.Focus,
        )

        assertFalse(content.alerts)
    }

    @Test
    fun crossingIntoABreakAlerts() {
        val resolved = PomodoroPolicy.resolve(session = session(), now = at(26))

        val content = PomodoroNotificationContent.from(
            session = resolved,
            now = at(26),
            previousPhase = PomodoroPhase.Focus,
        )

        assertEquals(PomodoroPhase.ShortBreak, content.phase)
        assertTrue(content.alerts)
    }

    /**
     * 세션을 방금 시작한 사용자는 화면을 보고 있다. 첫 알림까지 울릴 이유가 없다.
     */
    @Test
    fun theFirstNotificationOfASessionDoesNotAlert() {
        val content = PomodoroNotificationContent.from(
            session = session(),
            now = start,
            previousPhase = null,
        )

        assertFalse(content.alerts)
    }

    @Test
    fun anExpiredPhaseRendersZeroInsteadOfANegativeClock() {
        val content = PomodoroNotificationContent.from(
            session = session(),
            now = at(40),
            previousPhase = PomodoroPhase.Focus,
        )

        assertEquals("0:00", content.remainingClock)
    }
}
