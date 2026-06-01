package com.uiery.keep.util

import com.uiery.keep.model.RoutineModel

object RoutineRuntimePolicy {
    fun isAnyRoutineActive(
        routines: List<RoutineModel>,
        isRoutineActive: (RoutineModel) -> Boolean = ::isRoutineCurrentlyActive,
    ): Boolean = routines.any { routine ->
        routine.isEnabled && isRoutineActive(routine)
    }

    fun shouldBlockPackage(
        packageName: String,
        routines: List<RoutineModel>,
        isRoutineActive: (RoutineModel) -> Boolean = ::isRoutineCurrentlyActive,
    ): Boolean = findBlockingRoutine(
        packageName = packageName,
        routines = routines,
        isRoutineActive = isRoutineActive,
    ) != null

    fun findBlockingRoutine(
        packageName: String,
        routines: List<RoutineModel>,
        isRoutineActive: (RoutineModel) -> Boolean = ::isRoutineCurrentlyActive,
    ): RoutineModel? = routines.firstOrNull { routine ->
        routine.isEnabled &&
            routine.lockApplications?.contains(packageName) == true &&
            isRoutineActive(routine)
    }

    private fun isRoutineCurrentlyActive(routine: RoutineModel): Boolean =
        isRoutineActiveNow(
            startTime = routine.startTime,
            endTime = routine.endTime,
            repeatDays = routine.repeatDays.toDayOfWeekList(),
        )
}
