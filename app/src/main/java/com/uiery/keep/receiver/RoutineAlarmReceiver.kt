package com.uiery.keep.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.R
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.RoutineStore
import com.uiery.keep.model.toModel
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME)
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)

        RoutineReceiverPolicy.parseRoutineAlarmTrigger(
            action = action,
            routineName = routineName,
            routineId = routineId,
        ) ?: return

        val pendingResult = goAsync()
        ReceiverCoroutineRunner.launch(
            receiverName = "RoutineAlarmReceiver",
            finish = { pendingResult.finish() },
        ) {
            handleRoutineAlarm(
                action = action,
                routineName = routineName,
                routineId = routineId,
            )
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

        val notificationResult = notificationHelper.showRoutineStartNotification(
            trigger.routineName,
            trigger.routineId,
        )
        RoutineReceiverPolicy.buildPendingRoutineStartNotice(
            notificationResult = notificationResult,
            fallbackMessage = dataStoreFallbackMessage(trigger.routineName),
        )?.let { pendingNotice ->
            dataStore.edit { preferences ->
                RoutineReceiverPolicy.enqueuePendingRoutineStartNotice(
                    storedValue = preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
                    notice = pendingNotice,
                )?.let { encodedNotices ->
                    preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE] = encodedNotices
                }
            }
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

        RoutineReceiverPolicy.findEnabledRoutineToReschedule(
            routines = routines,
            routineId = trigger.routineId,
        )?.let { routineToReschedule ->
            when (routineScheduler.scheduleRoutine(routineToReschedule)) {
                RoutineScheduleResult.MissingExactAlarmPermission -> {
                    val recovery = RoutineReceiverPolicy.applyMissingExactAlarmPermission(
                        routines = routines,
                        routineId = routineToReschedule.id,
                    )
                    if (recovery.shouldResetAlarmPermissionPrompt) {
                        routineDao.updateIsEnabledById(routineToReschedule.id, false)
                        routineStore.writeCachedRoutines(recovery.routines)
                        dataStore.edit { preferences ->
                            preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] = false
                        }
                    }
                }
                RoutineScheduleResult.Scheduled,
                RoutineScheduleResult.NotEnabled,
                -> Unit
            }
        }
    }

    private fun dataStoreFallbackMessage(routineName: String): String =
        appContext.getString(R.string.routine_notification_permission_fallback_message, routineName)

    companion object {
        const val EXTRA_ROUTINE_NAME = "extra_routine_name"
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val ACTION_ROUTINE_ALARM = "com.uiery.keep.ACTION_ROUTINE_ALARM"
    }
}
