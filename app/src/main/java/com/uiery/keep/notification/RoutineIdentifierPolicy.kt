package com.uiery.keep.notification

import android.net.Uri
import java.time.DayOfWeek
import java.util.zip.CRC32

/**
 * Stable namespace for Android Int identifiers derived from Room routine Long ids.
 *
 * PendingIntent identity is also scoped by [alarmIntentData] so an extremely rare
 * Int hash collision cannot make two routine alarms equal.
 */
internal object RoutineIdentifierPolicy {
    fun alarmRequestCode(routineId: Long, dayOfWeek: DayOfWeek): Int {
        return stableHashToPositiveInt("routine_alarm:$routineId:${dayOfWeek.name}") and ALARM_REQUEST_CODE_MASK
    }

    fun alarmIntentData(routineId: Long, dayOfWeek: DayOfWeek): Uri {
        return Uri.parse(alarmIntentDataValue(routineId, dayOfWeek))
    }

    fun alarmIntentDataValue(routineId: Long, dayOfWeek: DayOfWeek): String {
        return "stopit://routine-alarm/$routineId/${dayOfWeek.name.lowercase()}"
    }

    fun routineStartNotificationId(routineId: Long): Int {
        return NOTIFICATION_ID_NAMESPACE or
            (stableHashToPositiveInt("routine_start_notification:$routineId") and NOTIFICATION_ID_MASK)
    }

    fun legacyAlarmRequestCode(routineId: Long, dayOfWeek: DayOfWeek): Int {
        return (routineId * 10 + dayOfWeek.ordinal).toInt()
    }

    private fun stableHashToPositiveInt(value: String): Int {
        val crc32 = CRC32()
        crc32.update(value.toByteArray(Charsets.UTF_8))
        return (crc32.value and Int.MAX_VALUE.toLong()).toInt()
    }

    private const val ALARM_REQUEST_CODE_MASK = 0x3FFF_FFFF
    private const val NOTIFICATION_ID_NAMESPACE = 0x4000_0000
    private const val NOTIFICATION_ID_MASK = 0x3FFF_FFFF
}
