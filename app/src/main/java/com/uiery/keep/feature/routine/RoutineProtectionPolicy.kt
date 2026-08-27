package com.uiery.keep.feature.routine

import com.uiery.keep.util.routineDurationMinutes
import java.time.DayOfWeek
import kotlinx.datetime.LocalTime

private const val MINUTES_PER_DAY = 24L * 60
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 60 * 60
private const val DAYS_PER_WEEK = 7

/**
 * 보호 잠금이 열리는 시각은 `시작 시각 - N시간`이라 자정을 넘어 전날로 넘어갈 수 있다.
 * 시:분만 보여 주면 그 하루 차이가 지워져서 "시작 직후"로 읽히므로, 안내 문구가 날짜가
 * 넘어갔는지를 알아야 한다.
 */
internal fun isChangeLockStartOnPreviousDay(
    startTime: LocalTime,
    changeLockHours: Int,
): Boolean = startTime.toSecondOfDay() - changeLockHours * SECONDS_PER_HOUR < 0

/**
 * 보호 잠금 창(`시작 - N시간` ~ `시작`)과 실행 창(`시작` ~ `종료`)은 맞붙어 있다. 둘을 합친
 * 길이가 회차 사이 간격까지 덮어 버리면 수정할 수 있는 틈이 하나도 남지 않는다. 그 조합인지
 * 판정한다.
 */
internal fun isRoutineProtectionAlwaysLocked(
    startTime: LocalTime,
    endTime: LocalTime,
    repeatDays: List<DayOfWeek>,
    changeLockHours: Int?,
): Boolean {
    val lockHours = changeLockHours ?: return false
    if (lockHours <= 0) return false

    val gapMinutes = maxOccurrenceGapMinutes(repeatDays)
    if (gapMinutes <= 0) return false

    val coveredMinutes = routineDurationMinutes(startTime, endTime) + lockHours * MINUTES_PER_HOUR
    return coveredMinutes >= gapMinutes
}

/**
 * 회차 사이가 가장 멀리 벌어지는 간격. 반복 요일이 듬성듬성하면 그 빈 요일이 곧 탈출구이므로,
 * 가장 넓은 간격조차 덮여야 비로소 상시 잠금이다.
 */
private fun maxOccurrenceGapMinutes(repeatDays: List<DayOfWeek>): Long {
    val days = repeatDays.distinct().sortedBy { it.value }
    if (days.isEmpty()) return 0
    return days.indices.maxOf { index ->
        val current = days[index].value
        val next = days[(index + 1) % days.size].value
        val dayGap = if (next > current) next - current else next + DAYS_PER_WEEK - current
        dayGap.toLong() * MINUTES_PER_DAY
    }
}
