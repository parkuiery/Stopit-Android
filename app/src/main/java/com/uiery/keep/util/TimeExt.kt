package com.uiery.keep.util

import android.content.Context
import com.uiery.keep.R
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

fun LocalTime.toTimeString(context: Context): String {
    val hour = this.hour
    val minute = this.minute
    val isAm = hour < 12
    val displayHour =
        when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
    val amPm = if (isAm) context.getString(R.string.am) else context.getString(R.string.pm)
    return "$amPm %02d:%02d".format(displayHour, minute)
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
