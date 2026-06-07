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

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
