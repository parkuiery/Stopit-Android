package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    lateinit var routineDao: RoutineDao

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(intent.action)) {
            return
        }

        val pendingResult = goAsync()
        ReceiverCoroutineRunner.launch(
            receiverName = "BootReceiver",
            finish = { pendingResult.finish() },
        ) {
            restoreRoutinesForBoot(intent.action)
        }
    }

    suspend fun restoreRoutinesForBoot(action: String?) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(action)) {
            return
        }

        val routineStore = RoutineStore(dataStore)
        val storedRoutines = routineStore.readCachedRoutines()
        val databaseRoutines = routineDao.fetchAllOnce().map { it.toModel() }
        var routines = RoutineReceiverPolicy.resolveRoutines(
            storedRoutines = storedRoutines,
            databaseRoutines = databaseRoutines,
        )

        var updatedRoutines = routines
        val disabledRoutineIds = linkedSetOf<Long>()
        var shouldResetAlarmPermissionPrompt = false
        routines.filter { it.isEnabled }.forEach { routine ->
            val scheduleApplication = RoutineReceiverPolicy.applyScheduleResult(
                routines = updatedRoutines,
                routineId = routine.id,
                scheduleResult = routineScheduler.scheduleRoutine(routine),
            )
            updatedRoutines = scheduleApplication.routines
            disabledRoutineIds += scheduleApplication.disabledRoutineIds
            shouldResetAlarmPermissionPrompt =
                shouldResetAlarmPermissionPrompt || scheduleApplication.shouldResetAlarmPermissionPrompt
        }

        disabledRoutineIds.forEach { routineId ->
            routineDao.updateIsEnabledById(routineId, false)
        }

        if (
            RoutineReceiverPolicy.shouldRehydrateStoredRoutines(storedRoutines, databaseRoutines) ||
            disabledRoutineIds.isNotEmpty()
        ) {
            routineStore.writeCachedRoutines(updatedRoutines)
        }

        if (shouldResetAlarmPermissionPrompt) {
            RoutineNoticeStore(dataStore).resetAlarmPermissionPrompt()
        }
    }
}
