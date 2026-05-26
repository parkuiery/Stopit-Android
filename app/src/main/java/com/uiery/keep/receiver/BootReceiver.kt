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
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreRoutinesForBoot(intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun restoreRoutinesForBoot(action: String?) {
        if (!RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(action)) {
            return
        }

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

        if (routines.isNotEmpty()) {
            routineScheduler.scheduleAllRoutines(routines)
        }
    }
}
