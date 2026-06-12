package com.uiery.keep.feature.routine

import android.content.Context
import com.uiery.keep.R
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.routineDurationMinutes
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.datetime.LocalTime
import java.time.DayOfWeek

private const val STOPIT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uiery.keep"

data class RoutineTemplateSharePayload(
    val templateCategory: RoutineTemplateCategory,
    val repeatDaysBucket: RoutineTemplateRepeatDaysBucket,
    val timeWindowBucket: RoutineTemplateTimeWindowBucket,
    val routineNameIncluded: Boolean,
    val routineName: String?,
    val durationMinutes: Long,
)

enum class RoutineTemplateCategory(
    val analyticsValue: String,
) {
    STUDY("study"),
    WORK("work"),
    NIGHT_FOCUS("night_focus"),
    CUSTOM("custom"),
}

enum class RoutineTemplateRepeatDaysBucket(
    val analyticsValue: String,
) {
    WEEKDAY("weekday"),
    WEEKEND("weekend"),
    DAILY("daily"),
    CUSTOM_DAYS("custom_days"),
    NONE("none"),
}

enum class RoutineTemplateTimeWindowBucket(
    val analyticsValue: String,
) {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night"),
    OVERNIGHT("overnight"),
    CUSTOM_WINDOW("custom_window"),
}

interface RoutineTemplateShareTextProvider {
    fun title(): String

    fun categoryLabel(category: RoutineTemplateCategory): String

    fun repeatDaysLabel(bucket: RoutineTemplateRepeatDaysBucket): String

    fun timeWindowLabel(bucket: RoutineTemplateTimeWindowBucket): String

    fun durationText(totalMinutes: Long): String

    fun callToAction(): String
}

class AndroidRoutineTemplateShareTextProvider(
    private val context: Context,
) : RoutineTemplateShareTextProvider {
    override fun title(): String = context.getString(R.string.routine_template_share_payload_title)

    override fun categoryLabel(category: RoutineTemplateCategory): String =
        context.getString(
            when (category) {
                RoutineTemplateCategory.STUDY -> R.string.routine_template_share_category_study
                RoutineTemplateCategory.WORK -> R.string.routine_template_share_category_work
                RoutineTemplateCategory.NIGHT_FOCUS -> R.string.routine_template_share_category_night_focus
                RoutineTemplateCategory.CUSTOM -> R.string.routine_template_share_category_custom
            },
        )

    override fun repeatDaysLabel(bucket: RoutineTemplateRepeatDaysBucket): String =
        context.getString(
            when (bucket) {
                RoutineTemplateRepeatDaysBucket.WEEKDAY -> R.string.routine_template_share_repeat_weekday
                RoutineTemplateRepeatDaysBucket.WEEKEND -> R.string.routine_template_share_repeat_weekend
                RoutineTemplateRepeatDaysBucket.DAILY -> R.string.routine_template_share_repeat_daily
                RoutineTemplateRepeatDaysBucket.CUSTOM_DAYS -> R.string.routine_template_share_repeat_custom_days
                RoutineTemplateRepeatDaysBucket.NONE -> R.string.routine_template_share_repeat_none
            },
        )

    override fun timeWindowLabel(bucket: RoutineTemplateTimeWindowBucket): String =
        context.getString(
            when (bucket) {
                RoutineTemplateTimeWindowBucket.MORNING -> R.string.routine_template_share_time_morning
                RoutineTemplateTimeWindowBucket.AFTERNOON -> R.string.routine_template_share_time_afternoon
                RoutineTemplateTimeWindowBucket.EVENING -> R.string.routine_template_share_time_evening
                RoutineTemplateTimeWindowBucket.NIGHT -> R.string.routine_template_share_time_night
                RoutineTemplateTimeWindowBucket.OVERNIGHT -> R.string.routine_template_share_time_overnight
                RoutineTemplateTimeWindowBucket.CUSTOM_WINDOW -> R.string.routine_template_share_time_custom_window
            },
        )

    override fun durationText(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> context.getString(
                R.string.routine_template_share_duration_hours_minutes,
                hours,
                minutes,
            )
            hours > 0L -> context.resources.getQuantityString(
                R.plurals.routine_template_share_duration_hours,
                hours.toInt(),
                hours,
            )
            else -> context.resources.getQuantityString(
                R.plurals.routine_template_share_duration_minutes,
                minutes.toInt(),
                minutes,
            )
        }
    }

    override fun callToAction(): String = context.getString(R.string.routine_template_share_payload_cta)
}

fun RoutineTemplateSharePayload.buildShareText(textProvider: RoutineTemplateShareTextProvider): String {
    val summary = listOf(
        textProvider.categoryLabel(templateCategory),
        textProvider.repeatDaysLabel(repeatDaysBucket),
        "${textProvider.timeWindowLabel(timeWindowBucket)} ${textProvider.durationText(durationMinutes)}",
    ).joinToString(" · ")

    return buildString {
        appendLine(textProvider.title())
        routineName?.let { appendLine(it) }
        appendLine(summary)
        appendLine(textProvider.callToAction())
        append(STOPIT_PLAY_STORE_URL)
    }
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
    val durationMinutes = routineDurationMinutes(routine.startTime, routine.endTime)
    val safeRoutineName = routine.name.trim().takeIf { includeRoutineName && it.isNotEmpty() && it.length <= 40 }

    return RoutineTemplateSharePayload(
        templateCategory = category,
        repeatDaysBucket = repeatBucket,
        timeWindowBucket = timeWindowBucket,
        routineNameIncluded = safeRoutineName != null,
        routineName = safeRoutineName,
        durationMinutes = durationMinutes,
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
