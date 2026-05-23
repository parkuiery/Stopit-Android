package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var routineScheduler: RoutineScheduler

    @Inject
    @KeepDataSource
    lateinit var dataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = intent.action,
            routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME),
            routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L),
        ) ?: return

        notificationHelper.showRoutineStartNotification(trigger.routineName, trigger.routineId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = dataStore.data.first()
                val routinesJson = preferences[PreferencesKey.ROUTINES]
                val routines = RoutineReceiverPolicy.decodeStoredRoutines(routinesJson)
                RoutineReceiverPolicy.findEnabledRoutineToReschedule(
                    routines = routines,
                    routineId = trigger.routineId,
                )?.let {
                    routineScheduler.scheduleRoutine(it)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ROUTINE_NAME = "extra_routine_name"
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val ACTION_ROUTINE_ALARM = "com.uiery.keep.ACTION_ROUTINE_ALARM"
    }
}
