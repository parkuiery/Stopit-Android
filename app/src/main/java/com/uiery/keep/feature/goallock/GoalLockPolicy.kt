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
    Completed,
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
        goalLock.selectedPackages.isNotEmpty() &&
            !goalLock.endDate.isBefore(goalLock.startDate) &&
            goalLock.lockMode.isValidForCreation()

    fun runtimeStatus(
        goalLock: GoalLock,
        now: LocalDateTime = LocalDateTime.now(),
    ): GoalLockRuntimeStatus {
        if (goalLock.status == GoalLockStoredStatus.EndedEarly) {
            return GoalLockRuntimeStatus.EndedEarly
        }
        if (goalLock.status == GoalLockStoredStatus.Completed) {
            return GoalLockRuntimeStatus.Completed
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
        if (goalLock.status != GoalLockStoredStatus.Active) return false

        return when (val mode = goalLock.lockMode) {
            GoalLockMode.AllDay -> runtimeStatus(goalLock, now) == GoalLockRuntimeStatus.Active
            is GoalLockMode.Scheduled -> isScheduledGoalLockActive(goalLock, mode, now)
        }
    }

    private fun isScheduledGoalLockActive(
        goalLock: GoalLock,
        mode: GoalLockMode.Scheduled,
        now: LocalDateTime,
    ): Boolean {
        val windowStartDate = scheduledWindowStartDate(mode, now) ?: return false
        return windowStartDate in goalLock.startDate..goalLock.endDate
    }

    private fun scheduledWindowStartDate(
        mode: GoalLockMode.Scheduled,
        now: LocalDateTime,
    ): LocalDate? {
        if (mode.repeatDays.isEmpty()) return null

        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val crossesMidnight = !mode.endTime.isAfter(mode.startTime)

        if (!crossesMidnight) {
            return today.takeIf {
                now.dayOfWeek in mode.repeatDays &&
                    !nowTime.isBefore(mode.startTime) &&
                    nowTime.isBefore(mode.endTime)
            }
        }

        val previousDate = today.minusDays(1)
        return when {
            now.dayOfWeek in mode.repeatDays && !nowTime.isBefore(mode.startTime) -> today
            previousDate.dayOfWeek in mode.repeatDays && nowTime.isBefore(mode.endTime) -> previousDate
            else -> null
        }
    }
}

private fun GoalLockMode.isValidForCreation(): Boolean = when (this) {
    GoalLockMode.AllDay -> true
    is GoalLockMode.Scheduled -> repeatDays.isNotEmpty() && startTime != endTime
}
