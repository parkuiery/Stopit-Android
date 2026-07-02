package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.datetime.toJavaLocalTime
import java.time.DayOfWeek
import java.time.LocalDateTime

enum class RoutineCardStatus {
    Enabled,
    Disabled,
    Running,
}

data class RoutineCardReadModel(
    val status: RoutineCardStatus,
    val repeatDays: List<DayOfWeek>,
    val nextRunAt: LocalDateTime?,
)

fun RoutineModel.toRoutineCardReadModel(
    now: LocalDateTime = LocalDateTime.now(),
): RoutineCardReadModel {
    val repeatDays = repeatDays.toDayOfWeekList()
    if (!isEnabled) {
        return RoutineCardReadModel(
            status = RoutineCardStatus.Disabled,
            repeatDays = repeatDays,
            nextRunAt = null,
        )
    }

    val isRunning = isRoutineActiveNow(
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays,
        nowDateTime = now,
    )

    return RoutineCardReadModel(
        status = if (isRunning) RoutineCardStatus.Running else RoutineCardStatus.Enabled,
        repeatDays = repeatDays,
        nextRunAt = findNextRoutineStart(
            repeatDays = repeatDays,
            startDateTime = now,
            startHourMinute = startTime.toJavaLocalTime(),
        ),
    )
}

private fun findNextRoutineStart(
    repeatDays: List<DayOfWeek>,
    startDateTime: LocalDateTime,
    startHourMinute: java.time.LocalTime,
): LocalDateTime? {
    if (repeatDays.isEmpty()) return null

    return (0..7)
        .asSequence()
        .map { daysAhead ->
            startDateTime
                .toLocalDate()
                .plusDays(daysAhead.toLong())
                .atTime(startHourMinute)
        }.firstOrNull { candidate ->
            candidate.dayOfWeek in repeatDays && candidate.isAfter(startDateTime)
        }
}
