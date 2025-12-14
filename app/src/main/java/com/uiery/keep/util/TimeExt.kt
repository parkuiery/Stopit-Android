package com.uiery.keep.util

import android.content.Context
import com.uiery.keep.R
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.DayOfWeek


val today: LocalDate
    inline get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

val now: LocalDateTime
    inline get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

val timeNow: kotlinx.datetime.LocalTime
    inline get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

fun LocalTime.toTimeString(context: Context): String {
    val hour = this.hour
    val minute = this.minute
    val isAm = hour < 12
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val amPm = if (isAm) context.getString(R.string.am) else context.getString(R.string.pm)
    return "$amPm %02d:%02d".format(displayHour, minute)
}

fun String.toDayOfWeekList(): List<DayOfWeek> {
    return this.padStart(7, '0')
        .mapIndexedNotNull { index, c ->
            if (c == '1') DayOfWeek.entries[index] else null
        }
}

fun List<DayOfWeek>.toRepeatDaysBinary(): String {
    return DayOfWeek.entries.joinToString("") { if (this.contains(it)) "1" else "0" }
}