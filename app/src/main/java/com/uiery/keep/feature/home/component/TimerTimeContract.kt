package com.uiery.keep.feature.home.component

import kotlinx.datetime.LocalTime

internal data class TimerDuration(
    val hours: Int,
    val minutes: Int,
) {
    val isPositive: Boolean = hours != 0 || minutes != 0
}

internal fun calculateTimerDuration(
    now: LocalTime,
    target: LocalTime,
): TimerDuration {
    val nowMinutes = now.hour * MINUTES_PER_HOUR + now.minute
    val targetMinutes = target.hour * MINUTES_PER_HOUR + target.minute
    val totalMinutes = when {
        targetMinutes == nowMinutes -> 0
        targetMinutes > nowMinutes -> targetMinutes - nowMinutes
        else -> MINUTES_PER_DAY - nowMinutes + targetMinutes
    }

    return TimerDuration(
        hours = totalMinutes / MINUTES_PER_HOUR,
        minutes = totalMinutes % MINUTES_PER_HOUR,
    )
}

internal fun timerPickerHourLabels(): List<String> =
    listOf("12") + (1..11).map { it.toString() }

internal fun timerPickerStartIndex(time: LocalTime): Int = time.hour % HOURS_PER_PERIOD

internal fun timerPickerSelectedTime(
    isPm: Boolean,
    hourLabel: String,
    minute: Int,
): LocalTime {
    val twelveHour = hourLabel.toInt()
    val baseHour = if (twelveHour == HOURS_PER_PERIOD) 0 else twelveHour
    val hour = if (isPm) baseHour + HOURS_PER_PERIOD else baseHour

    return LocalTime(hour = hour, minute = minute)
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_PERIOD = 12
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
