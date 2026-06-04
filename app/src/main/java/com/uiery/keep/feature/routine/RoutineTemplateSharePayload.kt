package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.routineDurationMinutes
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.datetime.LocalTime
import java.time.DayOfWeek

private const val STOPIT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uiery.keep"

data class RoutineTemplateSharePayload(
    val text: String,
    val templateCategory: RoutineTemplateCategory,
    val repeatDaysBucket: RoutineTemplateRepeatDaysBucket,
    val timeWindowBucket: RoutineTemplateTimeWindowBucket,
    val routineNameIncluded: Boolean,
)

enum class RoutineTemplateCategory(
    val analyticsValue: String,
    val label: String,
) {
    STUDY("study", "공부"),
    WORK("work", "업무"),
    NIGHT_FOCUS("night_focus", "야간 집중"),
    CUSTOM("custom", "집중"),
}

enum class RoutineTemplateRepeatDaysBucket(
    val analyticsValue: String,
    val label: String,
) {
    WEEKDAY("weekday", "평일"),
    WEEKEND("weekend", "주말"),
    DAILY("daily", "매일"),
    CUSTOM_DAYS("custom_days", "선택 요일"),
    NONE("none", "요일 미설정"),
}

enum class RoutineTemplateTimeWindowBucket(
    val analyticsValue: String,
    val label: String,
) {
    MORNING("morning", "아침"),
    AFTERNOON("afternoon", "오후"),
    EVENING("evening", "저녁"),
    NIGHT("night", "밤"),
    OVERNIGHT("overnight", "밤샘"),
    CUSTOM_WINDOW("custom_window", "선택 시간"),
}

internal fun buildRoutineTemplateSharePayload(
    routine: RoutineModel,
    includeRoutineName: Boolean = false,
): RoutineTemplateSharePayload? {
    val repeatDays = routine.repeatDays.toDayOfWeekList()
    if (repeatDays.isEmpty()) return null
    if (routine.startTime == routine.endTime) return null

    val category = routine.resolveTemplateCategory()
    val repeatBucket = repeatDays.resolveRepeatDaysBucket()
    val timeWindowBucket = resolveTimeWindowBucket(routine.startTime, routine.endTime)
    val durationText = formatDurationText(routineDurationMinutes(routine.startTime, routine.endTime))
    val safeRoutineName = routine.name.trim().takeIf { includeRoutineName && it.isNotEmpty() && it.length <= 40 }

    val summary = buildList {
        add(category.label)
        add(repeatBucket.label)
        add("${timeWindowBucket.label} $durationText")
    }.joinToString(" · ")

    val text = buildString {
        appendLine("스탑잇 집중 루틴 템플릿")
        safeRoutineName?.let { appendLine(it) }
        appendLine(summary)
        appendLine("나도 집중이 필요한 시간에 앱 사용을 잠깐 멈춰요.")
        append(STOPIT_PLAY_STORE_URL)
    }

    return RoutineTemplateSharePayload(
        text = text,
        templateCategory = category,
        repeatDaysBucket = repeatBucket,
        timeWindowBucket = timeWindowBucket,
        routineNameIncluded = safeRoutineName != null,
    )
}

private fun RoutineModel.resolveTemplateCategory(): RoutineTemplateCategory {
    val normalized = name.lowercase()
    return when {
        listOf("study", "exam", "test", "공부", "시험").any { it in normalized } -> RoutineTemplateCategory.STUDY
        listOf("work", "office", "업무", "일", "작업").any { it in normalized } -> RoutineTemplateCategory.WORK
        listOf("night", "sleep", "야간", "밤", "수면", "writing").any { it in normalized } -> RoutineTemplateCategory.NIGHT_FOCUS
        else -> RoutineTemplateCategory.CUSTOM
    }
}

private fun List<DayOfWeek>.resolveRepeatDaysBucket(): RoutineTemplateRepeatDaysBucket {
    val days = toSet()
    return when {
        days.isEmpty() -> RoutineTemplateRepeatDaysBucket.NONE
        days == DayOfWeek.entries.toSet() -> RoutineTemplateRepeatDaysBucket.DAILY
        days == setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ) -> RoutineTemplateRepeatDaysBucket.WEEKDAY
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> RoutineTemplateRepeatDaysBucket.WEEKEND
        else -> RoutineTemplateRepeatDaysBucket.CUSTOM_DAYS
    }
}

private fun resolveTimeWindowBucket(
    startTime: LocalTime,
    endTime: LocalTime,
): RoutineTemplateTimeWindowBucket =
    when {
        endTime <= startTime -> RoutineTemplateTimeWindowBucket.OVERNIGHT
        startTime.hour in 5..11 -> RoutineTemplateTimeWindowBucket.MORNING
        startTime.hour in 12..16 -> RoutineTemplateTimeWindowBucket.AFTERNOON
        startTime.hour in 17..21 -> RoutineTemplateTimeWindowBucket.EVENING
        startTime.hour >= 22 || startTime.hour < 5 -> RoutineTemplateTimeWindowBucket.NIGHT
        else -> RoutineTemplateTimeWindowBucket.CUSTOM_WINDOW
    }

private fun formatDurationText(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0L -> "${hours}시간"
        else -> "${minutes}분"
    }
}
