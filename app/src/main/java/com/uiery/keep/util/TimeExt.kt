package com.uiery.keep.util

import android.content.Context
import com.uiery.keep.R
import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.DayOfWeek
import java.time.Duration

val today: LocalDate
    inline get() =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

val now: LocalDateTime
    inline get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

val timeNow: kotlinx.datetime.LocalTime
    inline get() =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .time

fun formatTwelveHourTime(
    hour24: Int,
    minute: Int,
): String {
    val displayHour =
        when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
    return listOf(displayHour, minute).joinToString(":") { it.toString().padStart(2, '0') }
}

fun formatAmPmTime(
    amPm: String,
    hour24: Int,
    minute: Int,
): String = "$amPm ${formatTwelveHourTime(hour24 = hour24, minute = minute)}"

fun LocalTime.toTimeString(context: Context): String {
    val amPm = if (hour < 12) context.getString(R.string.am) else context.getString(R.string.pm)
    return formatAmPmTime(
        amPm = amPm,
        hour24 = hour,
        minute = minute,
    )
}

fun formatMinuteSecondCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun formatHourAwareCountdown(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        listOf(hours, minutes, seconds).joinToString(":") { it.toString().padStart(2, '0') }
    } else {
        listOf(minutes, seconds).joinToString(":") { it.toString().padStart(2, '0') }
    }
}

fun formatLockEndTime(
    lockTime: java.time.LocalDateTime,
    currentDate: java.time.LocalDate = java.time.LocalDate.now(),
): String =
    if (lockTime.toLocalDate() != currentDate) {
        "${lockTime.monthValue}/${lockTime.dayOfMonth} ${lockTime.hour.toString().padStart(2, '0')}:${lockTime.minute.toString().padStart(2, '0')}"
    } else {
        "${lockTime.hour.toString().padStart(2, '0')}:${lockTime.minute.toString().padStart(2, '0')}"
    }

fun String.toDayOfWeekList(): List<DayOfWeek> =
    this
        .padStart(7, '0')
        .mapIndexedNotNull { index, c ->
            if (c == '1') DayOfWeek.entries[index] else null
        }

fun List<DayOfWeek>.toRepeatDaysBinary(): String = DayOfWeek.entries.joinToString("") { if (this.contains(it)) "1" else "0" }

fun routineDurationMinutes(
    startTime: LocalTime,
    endTime: LocalTime,
): Long {
    val start = startTime.toJavaLocalTime()
    val end = endTime.toJavaLocalTime()
    val duration = Duration.between(start, end).toMinutes()
    return if (duration > 0) duration else duration + 24 * 60
}

fun isRoutineActiveNow(
    startTime: LocalTime,
    endTime: LocalTime,
    repeatDays: List<DayOfWeek>,
    nowDateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): Boolean {
    val nowTime = nowDateTime.toKotlinLocalDateTime().time
    val isCrossMidnight = !endTime.toJavaLocalTime().isAfter(startTime.toJavaLocalTime())

    if (!isCrossMidnight) {
        return nowDateTime.dayOfWeek in repeatDays &&
            nowTime >= startTime &&
            nowTime < endTime
    }

    val previousDay = nowDateTime.dayOfWeek.previousDay()
    val inTodayWindow = nowDateTime.dayOfWeek in repeatDays && nowTime >= startTime
    val inPreviousDayWindow = previousDay in repeatDays && nowTime < endTime

    return inTodayWindow || inPreviousDayWindow
}

fun currentRoutineWindowEndDateTime(
    startTime: LocalTime,
    endTime: LocalTime,
    nowDateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): java.time.LocalDateTime {
    val endToday = nowDateTime.toLocalDate().atTime(endTime.toJavaLocalTime())
    val isCrossMidnight = !endTime.toJavaLocalTime().isAfter(startTime.toJavaLocalTime())

    if (!isCrossMidnight) {
        return endToday
    }

    val nowTime = nowDateTime.toKotlinLocalDateTime().time
    return if (nowTime >= startTime) {
        endToday.plusDays(1)
    } else {
        endToday
    }
}

private fun DayOfWeek.previousDay(): DayOfWeek = if (this == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.of(this.value - 1)

fun isRoutineChangeLocked(
    startTime: kotlinx.datetime.LocalTime,
    repeatDays: List<java.time.DayOfWeek>,
    changeLockHours: Int,
    isEnabled: Boolean,
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): Boolean {
    if (!isEnabled) return false
    for (i in 0..6) {
        val candidateDate = now.toLocalDate().plusDays(i.toLong())
        if (candidateDate.dayOfWeek in repeatDays) {
            val candidateStart = candidateDate.atTime(
                startTime.hour, startTime.minute, startTime.second
            )
            val lockWindowStart = candidateStart.minusHours(changeLockHours.toLong())
            if (!now.isBefore(lockWindowStart) && now.isBefore(candidateStart)) {
                return true
            }
        }
    }
    return false
}

fun RoutineModel.isChangeLocked(
    now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): Boolean {
    val lockHours = changeLockHours ?: return false
    if (lockHours <= 0) return false
    return isRoutineChangeLocked(
        startTime = startTime,
        repeatDays = repeatDays.toDayOfWeekList(),
        changeLockHours = lockHours,
        isEnabled = isEnabled,
        now = now,
    )
}

fun RoutineModel.isRunningNow(
    nowDateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
): Boolean {
    if (!isEnabled) return false
    return isRoutineActiveNow(
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toDayOfWeekList(),
        nowDateTime = nowDateTime,
    )
}
