package com.uiery.keep.feature.routine

internal sealed interface RoutineListAction {
    data class ToggleEnabled(
        val routineId: Long,
        val isEnabled: Boolean,
    ) : RoutineListAction

    data object Blocked : RoutineListAction
}

internal fun resolveRoutineEnabledSwitchAction(
    routineId: Long,
    requestedEnabled: Boolean,
    isBlocked: Boolean,
): RoutineListAction = if (isBlocked) {
    RoutineListAction.Blocked
} else {
    RoutineListAction.ToggleEnabled(
        routineId = routineId,
        isEnabled = requestedEnabled,
    )
}
