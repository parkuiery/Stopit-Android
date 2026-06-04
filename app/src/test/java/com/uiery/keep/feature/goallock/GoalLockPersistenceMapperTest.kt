package com.uiery.keep.feature.goallock

import com.uiery.keep.database.entity.GoalLockEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class GoalLockPersistenceMapperTest {
    @Test
    fun allDayGoalLockRoundTripsThroughEntityWithoutScheduleFields() {
        val goalLock = GoalLock(
            id = 7,
            goalName = "시험 준비",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 30),
            lockMode = GoalLockMode.AllDay,
            selectedPackages = setOf("com.video", "com.social"),
            status = GoalLockStoredStatus.Active,
        )

        val entity = GoalLockEntity.fromDomain(goalLock)
        val restored = entity.toDomain()

        assertEquals(7L, entity.id)
        assertEquals("시험 준비", entity.goalName)
        assertEquals("2026-06-01", entity.startDate)
        assertEquals("2026-06-30", entity.endDate)
        assertEquals("all_day", entity.lockMode)
        assertEquals(null, entity.repeatDays)
        assertEquals(null, entity.startTime)
        assertEquals(null, entity.endTime)
        assertEquals(listOf("com.video", "com.social"), entity.selectedPackages)
        assertEquals("active", entity.status)
        assertEquals(goalLock, restored)
    }

    @Test
    fun scheduledGoalLockRoundTripsThroughEntityWithRepeatDaysAndTimes() {
        val goalLock = GoalLock(
            id = 8,
            goalName = "SNS 줄이기",
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 14),
            lockMode = GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(23, 0),
            ),
            selectedPackages = setOf("com.chat"),
            status = GoalLockStoredStatus.EndedEarly,
        )

        val entity = GoalLockEntity.fromDomain(goalLock)
        val restored = entity.toDomain()

        assertEquals("scheduled", entity.lockMode)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), entity.repeatDays)
        assertEquals("19:00", entity.startTime)
        assertEquals("23:00", entity.endTime)
        assertEquals("ended_early", entity.status)
        assertEquals(goalLock, restored)
    }
}
