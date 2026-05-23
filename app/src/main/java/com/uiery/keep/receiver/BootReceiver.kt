package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (RoutineReceiverPolicy.shouldRestoreRoutinesOnBoot(intent.action)) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val preferences = dataStore.data.first()
                    val routinesJson = preferences[PreferencesKey.ROUTINES]

                    val routines = RoutineReceiverPolicy.decodeStoredRoutines(routinesJson)
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
