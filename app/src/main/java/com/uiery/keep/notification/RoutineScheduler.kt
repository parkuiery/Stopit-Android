package com.uiery.keep.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.receiver.RoutineAlarmReceiver
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

@Singleton
class RoutineScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleRoutine(routine: RoutineModel) {
        Log.d("TEST", "scheduleRoutine: $routine")
        if (!routine.isEnabled) {
            cancelRoutine(routine.id)
            return
    }

        val repeatDays = routine.repeatDays.toDayOfWeekList()
        if (repeatDays.isEmpty()) return

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextAlarmTime,
                        pendingIntent
                    )
                } else {
                    // 알림 메니저 권한 없음
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime,
                    pendingIntent
                )
            }
        }
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
}
