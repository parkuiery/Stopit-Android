package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        if (RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(intent.action)) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val preferences = dataStore.data.first()
                    val storedRoutines = RoutineReceiverPolicy.decodeStoredRoutines(preferences[PreferencesKey.ROUTINES])
                    val databaseRoutines = if (storedRoutines.isEmpty()) {
                        routineDao.fetchAllOnce().map { it.toModel() }
                    } else {
                        emptyList()
                    }
                    val routines = RoutineReceiverPolicy.resolveRoutines(
                        storedRoutines = storedRoutines,
                        databaseRoutines = databaseRoutines,
                    )

                    if (RoutineReceiverPolicy.shouldRehydrateStoredRoutines(storedRoutines, databaseRoutines)) {
                        dataStore.edit { mutablePreferences ->
                            mutablePreferences[PreferencesKey.ROUTINES] = Json.encodeToString(routines)
                        }
                    }

                    if (routines.isNotEmpty()) {
                        routineScheduler.scheduleAllRoutines(routines)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
