package com.uiery.keep.notification

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.receiver.RoutineAlarmReceiver
import com.uiery.keep.util.AppLogger
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

enum class RoutineScheduleResult {
    Scheduled,
    NotEnabled,
    MissingExactAlarmPermission,
}

@Singleton
class RoutineScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val appOpsMode = appOpsManager.unsafeCheckOpNoThrow(
            EXACT_ALARM_APP_OP,
            context.applicationInfo.uid,
            context.packageName,
        )
        val alarmManagerAllowed = alarmManager.canScheduleExactAlarms()
        val canSchedule = resolveExactAlarmAvailability(
            appOpsMode = appOpsMode,
            alarmManagerAllowed = alarmManagerAllowed,
        )
        AppLogger.debug(
            "RoutineScheduler",
            "canScheduleExactAlarms package=${context.packageName} uid=${context.applicationInfo.uid} sdk=${Build.VERSION.SDK_INT} appOpsMode=$appOpsMode alarmManagerAllowed=$alarmManagerAllowed result=$canSchedule",
        )

        return canSchedule
    }

    fun scheduleRoutine(routine: RoutineModel): RoutineScheduleResult {
        if (!routine.isEnabled) {
            cancelRoutine(routine.id)
            return RoutineScheduleResult.NotEnabled
        }

        val repeatDays = routine.repeatDays.toDayOfWeekList()
        if (repeatDays.isEmpty()) return RoutineScheduleResult.NotEnabled

        val hasExactAlarmPermission = canScheduleExactAlarms()
        AppLogger.debug(
            "RoutineScheduler",
            "scheduleRoutine exact-alarm precheck routineId=${routine.id} result=$hasExactAlarmPermission",
        )
        if (!hasExactAlarmPermission) {
            cancelRoutine(routine.id)
            return RoutineScheduleResult.MissingExactAlarmPermission
        }

        repeatDays.forEach { dayOfWeek ->
            val nextAlarmTime = calculateNextAlarmTime(routine.startTime, dayOfWeek)
            val requestCode = (routine.id * 10 + dayOfWeek.ordinal).toInt()

            val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
                putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_NAME, routine.name)
                putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, routine.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
            } catch (securityException: SecurityException) {
                cancelRoutine(routine.id)
                return RoutineScheduleResult.MissingExactAlarmPermission
            }
        }

        return RoutineScheduleResult.Scheduled
    }

    fun cancelRoutine(routineId: Long) {
        DayOfWeek.entries.forEach { dayOfWeek ->
            val requestCode = (routineId * 10 + dayOfWeek.ordinal).toInt()

            val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = RoutineAlarmReceiver.ACTION_ROUTINE_ALARM
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleAllRoutines(routines: List<RoutineModel>) {
        routines.filter { it.isEnabled }.forEach { routine ->
            scheduleRoutine(routine)
        }
    }

    private fun calculateNextAlarmTime(startTime: kotlinx.datetime.LocalTime, dayOfWeek: DayOfWeek): Long {
        val currentDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentDate = currentDateTime.date
        val currentTime = currentDateTime.time

        // Convert java.time.DayOfWeek to kotlinx.datetime.DayOfWeek value (0 = MONDAY, 6 = SUNDAY)
        val targetDayValue = dayOfWeek.ordinal
        val currentDayValue = currentDate.dayOfWeek.ordinal

        val daysToAdd = when {
            targetDayValue > currentDayValue -> targetDayValue - currentDayValue
            targetDayValue < currentDayValue -> 7 - (currentDayValue - targetDayValue)
            else -> {
                // Same day - check if time has passed
                if (startTime > currentTime) 0 else 7
            }
        }

        // Use kotlinx.datetime for safe date addition
        val targetDate = currentDate.plus(daysToAdd, DateTimeUnit.DAY)
        val targetDateTime = LocalDateTime(targetDate, startTime)
        return targetDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    private companion object {
        private const val EXACT_ALARM_APP_OP = "android:schedule_exact_alarm"
    }
}

internal fun resolveExactAlarmAvailability(
    appOpsMode: Int,
    alarmManagerAllowed: Boolean,
): Boolean {
    if (!alarmManagerAllowed) return false

    return when (appOpsMode) {
        AppOpsManager.MODE_ALLOWED,
        AppOpsManager.MODE_DEFAULT -> true
        else -> false
    }
}
