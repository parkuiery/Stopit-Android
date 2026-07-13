package com.uiery.keep.feature.goallock

import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal sealed interface GoalLockProgress {
    data class Pending(val daysUntilStart: Long) : GoalLockProgress

    data class Active(val remainingDaysIncludingToday: Long) : GoalLockProgress

    data class Completed(
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : GoalLockProgress

    data object EndedEarly : GoalLockProgress
}

internal data class GoalLockDetailPresentation(
    val runtimeStatus: GoalLockRuntimeStatus,
    val canEdit: Boolean,
    val canEnd: Boolean,
    val totalDurationDays: Long,
    val progress: GoalLockProgress,
)

internal fun goalLockDetailPresentation(
    goalLock: GoalLock,
    today: LocalDate,
): GoalLockDetailPresentation {
    val runtimeStatus = GoalLockPolicy.runtimeStatus(goalLock, today.atStartOfDay())
    val isCurrent = goalLock.status == GoalLockStoredStatus.Active &&
        runtimeStatus in setOf(GoalLockRuntimeStatus.Pending, GoalLockRuntimeStatus.Active)
    val progress = when (runtimeStatus) {
        GoalLockRuntimeStatus.Pending -> GoalLockProgress.Pending(
            daysUntilStart = ChronoUnit.DAYS.between(today, goalLock.startDate).coerceAtLeast(0),
        )
        GoalLockRuntimeStatus.Active -> GoalLockProgress.Active(
            remainingDaysIncludingToday = ChronoUnit.DAYS.between(today, goalLock.endDate)
                .coerceAtLeast(0) + 1,
        )
        GoalLockRuntimeStatus.Completed -> GoalLockProgress.Completed(
            startDate = goalLock.startDate,
            endDate = goalLock.endDate,
        )
        GoalLockRuntimeStatus.EndedEarly -> GoalLockProgress.EndedEarly
    }

    return GoalLockDetailPresentation(
        runtimeStatus = runtimeStatus,
        canEdit = isCurrent,
        canEnd = isCurrent,
        totalDurationDays = ChronoUnit.DAYS.between(goalLock.startDate, goalLock.endDate)
            .coerceAtLeast(0) + 1,
        progress = progress,
    )
}
