package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import javax.inject.Inject

data class RoutineExactAlarmScheduleDecision(
    val routine: RoutineModel,
    val shouldShowPermissionPrompt: Boolean,
    val shouldTrackLockScheduled: Boolean,
)

class RoutineExactAlarmOrchestrator
    @Inject
    constructor(
        private val routineScheduler: RoutineScheduler,
    ) {
        fun canScheduleExactAlarms(): Boolean = routineScheduler.canScheduleExactAlarms()

        fun resolveBeforePersist(routine: RoutineModel): RoutineExactAlarmPermissionResult =
            resolveRoutineExactAlarmPermission(
                routine = routine,
                canScheduleExactAlarms = canScheduleExactAlarms(),
            )

        fun scheduleEnabledRoutine(routine: RoutineModel): RoutineExactAlarmScheduleDecision {
            if (!routine.isEnabled) {
                return RoutineExactAlarmScheduleDecision(
                    routine = routine,
                    shouldShowPermissionPrompt = false,
                    shouldTrackLockScheduled = false,
                )
            }

            return when (routineScheduler.scheduleRoutine(routine)) {
                RoutineScheduleResult.Scheduled -> RoutineExactAlarmScheduleDecision(
                    routine = routine,
                    shouldShowPermissionPrompt = false,
                    shouldTrackLockScheduled = true,
                )
                RoutineScheduleResult.MissingExactAlarmPermission -> RoutineExactAlarmScheduleDecision(
                    routine = routine.copy(isEnabled = false),
                    shouldShowPermissionPrompt = true,
                    shouldTrackLockScheduled = false,
                )
                RoutineScheduleResult.NotEnabled -> RoutineExactAlarmScheduleDecision(
                    routine = routine,
                    shouldShowPermissionPrompt = false,
                    shouldTrackLockScheduled = false,
                )
            }
        }

        fun cancelRoutine(id: Long) {
            routineScheduler.cancelRoutine(id)
        }
    }
