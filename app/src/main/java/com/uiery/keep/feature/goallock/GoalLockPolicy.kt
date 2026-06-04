package com.uiery.keep.feature.goallock

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal data class GoalLock(
    val id: Long,
    val goalName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lockMode: GoalLockMode,
    val selectedPackages: Set<String>,
    val status: GoalLockStoredStatus,
)

internal sealed interface GoalLockMode {
    data object AllDay : GoalLockMode

    data class Scheduled(
        val repeatDays: Set<DayOfWeek>,
        val startTime: LocalTime,
        val endTime: LocalTime,
    ) : GoalLockMode
}

internal enum class GoalLockStoredStatus {
    Active,
    EndedEarly,
}

internal enum class GoalLockRuntimeStatus {
    Pending,
    Active,
    Completed,
    EndedEarly,
}

internal object GoalLockPolicy {
    fun isValidForCreation(goalLock: GoalLock): Boolean =
        goalLock.selectedPackages.isNotEmpty() && !goalLock.endDate.isBefore(goalLock.startDate)

    fun runtimeStatus(
        goalLock: GoalLock,
        now: LocalDateTime = LocalDateTime.now(),
    ): GoalLockRuntimeStatus {
        if (goalLock.status == GoalLockStoredStatus.EndedEarly) {
            return GoalLockRuntimeStatus.EndedEarly
        }

        val today = now.toLocalDate()
        return when {
            today.isBefore(goalLock.startDate) -> GoalLockRuntimeStatus.Pending
            today.isAfter(goalLock.endDate) -> GoalLockRuntimeStatus.Completed
            else -> GoalLockRuntimeStatus.Active
        }
    }

    fun isBlocking(
        goalLock: GoalLock,
        packageName: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        if (!isValidForCreation(goalLock)) return false
        if (packageName !in goalLock.selectedPackages) return false
        if (runtimeStatus(goalLock, now) != GoalLockRuntimeStatus.Active) return false

        return when (val mode = goalLock.lockMode) {
            GoalLockMode.AllDay -> true
            is GoalLockMode.Scheduled -> isScheduledWindowActive(mode, now)
        }
    }

    private fun isScheduledWindowActive(
        mode: GoalLockMode.Scheduled,
        now: LocalDateTime,
    ): Boolean {
        if (mode.repeatDays.isEmpty()) return false

        val nowTime = now.toLocalTime()
        val crossesMidnight = !mode.endTime.isAfter(mode.startTime)

        if (!crossesMidnight) {
            return now.dayOfWeek in mode.repeatDays &&
                !nowTime.isBefore(mode.startTime) &&
                nowTime.isBefore(mode.endTime)
        }

        val previousDay = now.dayOfWeek.previousDay()
        val inTodayWindow = now.dayOfWeek in mode.repeatDays &&
            !nowTime.isBefore(mode.startTime)
        val inPreviousDayWindow = previousDay in mode.repeatDays &&
            nowTime.isBefore(mode.endTime)

        return inTodayWindow || inPreviousDayWindow
    }

    private fun DayOfWeek.previousDay(): DayOfWeek =
        if (this == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.of(value - 1)
}
