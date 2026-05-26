package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    lateinit var routineDao: RoutineDao

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = intent.action,
            routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME),
            routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L),
        ) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleRoutineAlarm(
                    action = intent.action,
                    routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME),
                    routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun handleRoutineAlarm(
        action: String?,
        routineName: String?,
        routineId: Long,
    ) {
        val trigger = RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = action,
            routineName = routineName,
            routineId = routineId,
        ) ?: return

        notificationHelper.showRoutineStartNotification(trigger.routineName, trigger.routineId)

        val routineStore = RoutineStore(dataStore)
        val storedRoutines = routineStore.readCachedRoutines()
        val databaseRoutines = routineDao.fetchAllOnce().map { it.toModel() }
        val routines = RoutineReceiverPolicy.resolveRoutines(
            storedRoutines = storedRoutines,
            databaseRoutines = databaseRoutines,
        )

        if (RoutineReceiverPolicy.shouldRehydrateStoredRoutines(storedRoutines, databaseRoutines)) {
            routineStore.writeCachedRoutines(routines)
        }

        RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = routines,
            routineId = trigger.routineId,
        )?.let {
            routineScheduler.scheduleRoutine(it)
        }
    }

    companion object {
        const val EXTRA_ROUTINE_NAME = "extra_routine_name"
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val ACTION_ROUTINE_ALARM = "com.uiery.keep.ACTION_ROUTINE_ALARM"
    }
}
