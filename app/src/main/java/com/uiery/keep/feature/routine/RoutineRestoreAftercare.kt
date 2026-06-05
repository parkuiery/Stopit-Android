package com.uiery.keep.feature.routine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import javax.inject.Inject

class RoutineRestoreAftercare
    @Inject
    constructor(
        private val routineDao: RoutineDao,
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
        private val routineNoticeStore: RoutineNoticeStore,
    ) {
        suspend fun rescheduleRestoredEnabledRoutines(routines: List<RoutineModel>): RoutineRestoreRescheduleResult {
            var restoredRoutines = routines
            var shouldShowAlarmPermissionPrompt = false
            routines.filter { it.isEnabled }.forEach { routine ->
                val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(routine)
                if (scheduleDecision.routine.isEnabled != routine.isEnabled) {
                    routineDao.updateIsEnabledById(routine.id, scheduleDecision.routine.isEnabled)
                    restoredRoutines = restoredRoutines.map { current ->
                        if (current.id == routine.id) scheduleDecision.routine else current
                    }
                }
                shouldShowAlarmPermissionPrompt =
                    shouldShowAlarmPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt
            }

            if (shouldShowAlarmPermissionPrompt) {
                routineNoticeStore.resetAlarmPermissionPrompt()
            }

            return RoutineRestoreRescheduleResult(
                routines = restoredRoutines,
                shouldShowAlarmPermissionPrompt = shouldShowAlarmPermissionPrompt,
            )
        }

        suspend fun rescheduleRestoredEnabledRoutinesFromRoom(): RoutineRestoreRescheduleResult {
            val routines = routineDao.fetchAllOnce().map { it.toModel() }
            val result = rescheduleRestoredEnabledRoutines(routines)
            RoutineStore(dataStore).writeCachedRoutines(result.routines)
            return result
        }
    }

data class RoutineRestoreRescheduleResult(
    val routines: List<RoutineModel>,
    val shouldShowAlarmPermissionPrompt: Boolean,
)
