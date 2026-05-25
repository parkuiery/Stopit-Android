package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel

data class RoutineExactAlarmPermissionResult(
    val routine: RoutineModel,
    val shouldShowPermissionPrompt: Boolean,
)

fun resolveRoutineExactAlarmPermission(
    routine: RoutineModel,
    canScheduleExactAlarms: Boolean,
): RoutineExactAlarmPermissionResult {
    if (!routine.isEnabled) {
        return RoutineExactAlarmPermissionResult(
            routine = routine,
            shouldShowPermissionPrompt = false,
        )
    }

    if (canScheduleExactAlarms) {
        return RoutineExactAlarmPermissionResult(
            routine = routine,
            shouldShowPermissionPrompt = false,
        )
    }

    return RoutineExactAlarmPermissionResult(
        routine = routine.copy(isEnabled = false),
        shouldShowPermissionPrompt = true,
    )
}
