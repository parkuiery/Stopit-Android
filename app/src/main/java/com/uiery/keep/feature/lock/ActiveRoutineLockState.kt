package com.uiery.keep.feature.lock

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.currentRoutineWindowEndDateTime
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDateTime

internal data class ActiveRoutineLockState(
    val routines: List<RoutineModel>,
    val blockedApps: Set<String>,
    val endTime: LocalDateTime,
)

internal fun resolveActiveRoutineLockState(
    routines: List<RoutineModel>,
    nowDateTime: LocalDateTime = LocalDateTime.now(),
): ActiveRoutineLockState {
    val activeRoutines =
        routines.filter { routine ->
            routine.isEnabled &&
                isRoutineActiveNow(
                    startTime = routine.startTime,
                    endTime = routine.endTime,
                    repeatDays = routine.repeatDays.toDayOfWeekList(),
                    nowDateTime = nowDateTime,
                )
        }

    if (activeRoutines.isEmpty()) {
        return ActiveRoutineLockState(
            routines = emptyList(),
            blockedApps = emptySet(),
            endTime = nowDateTime,
        )
    }

    val blockedApps =
        activeRoutines
            .flatMap { it.lockApplications.orEmpty() }
            .toCollection(linkedSetOf())
    val endTime =
        activeRoutines.maxOf { routine ->
            currentRoutineWindowEndDateTime(
                startTime = routine.startTime,
                endTime = routine.endTime,
                nowDateTime = nowDateTime,
            )
        }

    return ActiveRoutineLockState(
        routines = activeRoutines,
        blockedApps = blockedApps,
        endTime = endTime,
    )
}
