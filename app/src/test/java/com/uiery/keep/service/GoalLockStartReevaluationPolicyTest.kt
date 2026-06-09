package com.uiery.keep.service

import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GoalLockStartReevaluationPolicyTest {
    @Test
    fun nextGoalLockStartReevaluationDelayReturnsDelayUntilAllDayStartDateMidnight() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 10),
                    endDate = LocalDate.of(2026, 6, 12),
                    lockMode = GoalLockMode.AllDay,
                ),
            ),
            now = LocalDateTime.of(2026, 6, 9, 23, 30),
        )

        assertEquals(30 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextGoalLockStartReevaluationDelayReturnsDelayUntilScheduledStartToday() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(23, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 18, 45),
        )

        assertEquals(15 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextGoalLockStartReevaluationDelayWrapsScheduledStartToNextRepeatDayWithinDateRange() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.THURSDAY),
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 10, 0),
        )

        assertEquals(23 * 60 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextGoalLockStartReevaluationDelaySupportsOvernightScheduledStart() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = LocalTime.of(23, 0),
                        endTime = LocalTime.of(1, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 22, 45),
        )

        assertEquals(15 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextGoalLockStartReevaluationDelayReturnsSoonestFutureGoalLockStart() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    id = 1L,
                    startDate = LocalDate.of(2026, 6, 11),
                    lockMode = GoalLockMode.AllDay,
                ),
                goalLock(
                    id = 2L,
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = LocalTime.of(20, 0),
                        endTime = LocalTime.of(23, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 19, 30),
        )

        assertEquals(30 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextGoalLockStartReevaluationDelaySkipsInactiveInvalidAndExpiredGoalLocks() {
        val delayMillis = nextGoalLockStartReevaluationDelayMillis(
            goalLocks = listOf(
                goalLock(
                    id = 1L,
                    startDate = LocalDate.of(2026, 6, 10),
                    status = GoalLockStoredStatus.Completed,
                ),
                goalLock(
                    id = 2L,
                    startDate = LocalDate.of(2026, 6, 10),
                    selectedPackages = emptySet(),
                ),
                goalLock(
                    id = 3L,
                    startDate = LocalDate.of(2026, 6, 10),
                    endDate = LocalDate.of(2026, 6, 9),
                ),
                goalLock(
                    id = 4L,
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 9),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(23, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 18, 0),
        )

        assertNull(delayMillis)
    }

    @Test
    fun nextTimeBasedBlockingStartReevaluationDelayCombinesRoutineAndGoalLockStarts() {
        val delayMillis = nextTimeBasedBlockingStartReevaluationDelayMillis(
            routines = listOf(
                routine(
                    startTime = kotlinx.datetime.LocalTime(20, 0),
                    repeatDay = DayOfWeek.WEDNESDAY,
                ),
            ),
            goalLocks = listOf(
                goalLock(
                    startDate = LocalDate.of(2026, 6, 1),
                    endDate = LocalDate.of(2026, 6, 30),
                    lockMode = GoalLockMode.Scheduled(
                        repeatDays = setOf(DayOfWeek.WEDNESDAY),
                        startTime = LocalTime.of(19, 0),
                        endTime = LocalTime.of(23, 0),
                    ),
                ),
            ),
            now = LocalDateTime.of(2026, 6, 10, 18, 45),
        )

        assertEquals(15 * 60 * 1_000L, delayMillis)
    }

    private fun goalLock(
        id: Long = 1L,
        startDate: LocalDate = LocalDate.of(2026, 6, 1),
        endDate: LocalDate = LocalDate.of(2026, 6, 30),
        lockMode: GoalLockMode = GoalLockMode.AllDay,
        selectedPackages: Set<String> = setOf("com.video.app"),
        status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
    ) = GoalLock(
        id = id,
        goalName = "Goal $id",
        startDate = startDate,
        endDate = endDate,
        lockMode = lockMode,
        selectedPackages = selectedPackages,
        status = status,
    )

    private fun routine(
        id: Long = 1L,
        startTime: kotlinx.datetime.LocalTime,
        repeatDay: DayOfWeek,
    ) = com.uiery.keep.model.RoutineModel(
        id = id,
        name = "Routine $id",
        startTime = startTime,
        endTime = kotlinx.datetime.LocalTime(23, 0),
        repeatDays = repeatDayBinary(repeatDay),
        lockApplications = listOf("com.example.blocked"),
        isEnabled = true,
    )

    private fun repeatDayBinary(dayOfWeek: DayOfWeek): String = buildString {
        DayOfWeek.entries.forEach { day ->
            append(if (day == dayOfWeek) '1' else '0')
        }
    }
}
