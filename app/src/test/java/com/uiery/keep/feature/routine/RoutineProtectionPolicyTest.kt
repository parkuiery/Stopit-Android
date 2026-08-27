package com.uiery.keep.feature.routine

import java.time.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineProtectionPolicyTest {

    private val everyDay = DayOfWeek.entries.toList()
    private val weekdays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    @Test
    fun `잠금 시작이 자정을 넘어가면 전날로 판정한다`() {
        assertTrue(isChangeLockStartOnPreviousDay(LocalTime(1, 0), changeLockHours = 23))
        assertTrue(isChangeLockStartOnPreviousDay(LocalTime(1, 0), changeLockHours = 2))
        assertTrue(isChangeLockStartOnPreviousDay(LocalTime(0, 0), changeLockHours = 1))
    }

    @Test
    fun `잠금 시작이 같은 날에 남으면 전날이 아니다`() {
        assertFalse(isChangeLockStartOnPreviousDay(LocalTime(1, 0), changeLockHours = 1))
        assertFalse(isChangeLockStartOnPreviousDay(LocalTime(5, 0), changeLockHours = 5))
        assertFalse(isChangeLockStartOnPreviousDay(LocalTime(23, 30), changeLockHours = 12))
    }

    @Test
    fun `24시간 전은 시작 시각이 같아도 전날이다`() {
        assertTrue(isChangeLockStartOnPreviousDay(LocalTime(1, 0), changeLockHours = 24))
    }

    @Test
    fun `VOC 사례 - 매일 새벽 1시부터 5시 루틴에 23시간 보호는 상시 잠금이다`() {
        assertTrue(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = everyDay,
                changeLockHours = 23,
            ),
        )
    }

    @Test
    fun `잠금 창과 실행 창의 합이 하루에 못 미치면 상시 잠금이 아니다`() {
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = everyDay,
                changeLockHours = 19,
            ),
        )
    }

    @Test
    fun `매일 반복은 잠금 창과 실행 창의 합이 정확히 24시간일 때부터 상시 잠금이다`() {
        // 22시 시작, 익일 6시 종료 = 8시간 실행. 16시간 보호면 합이 정확히 24시간이다.
        assertTrue(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(22, 0),
                endTime = LocalTime(6, 0),
                repeatDays = everyDay,
                changeLockHours = 16,
            ),
        )
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(22, 0),
                endTime = LocalTime(6, 0),
                repeatDays = everyDay,
                changeLockHours = 15,
            ),
        )
    }

    @Test
    fun `쉬는 요일이 있으면 최대 보호 시간으로도 상시 잠금이 되지 않는다`() {
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = weekdays,
                changeLockHours = 24,
            ),
        )
    }

    @Test
    fun `보호를 끄거나 0시간이면 상시 잠금이 아니다`() {
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = everyDay,
                changeLockHours = null,
            ),
        )
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = everyDay,
                changeLockHours = 0,
            ),
        )
    }

    @Test
    fun `반복 요일이 없으면 회차 자체가 없으므로 상시 잠금이 아니다`() {
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(5, 0),
                repeatDays = emptyList(),
                changeLockHours = 24,
            ),
        )
    }

    @Test
    fun `하루만 반복하면 회차 간격이 일주일이라 상시 잠금이 될 수 없다`() {
        assertFalse(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(1, 0),
                repeatDays = listOf(DayOfWeek.WEDNESDAY),
                changeLockHours = 24,
            ),
        )
    }

    @Test
    fun `이틀 걸러 반복해도 잠금이 48시간을 덮으면 상시 잠금이다`() {
        // 하루 종일 도는 루틴(24시간)에 24시간 보호면 회차당 48시간을 덮는다.
        assertTrue(
            isRoutineProtectionAlwaysLocked(
                startTime = LocalTime(1, 0),
                endTime = LocalTime(1, 0),
                repeatDays = listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SUNDAY,
                ),
                changeLockHours = 24,
            ),
        )
    }
}
