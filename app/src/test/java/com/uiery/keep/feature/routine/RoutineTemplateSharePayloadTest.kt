package com.uiery.keep.feature.routine

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class RoutineTemplateSharePayloadTest {
    @Test
    fun buildRoutineTemplateSharePayloadExcludesBlockedAppsAndRoutineNameByDefault() {
        val routine = routine(
            name = "Secret exam detox",
            repeatDays = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            startTime = LocalTime(hour = 19, minute = 0),
            endTime = LocalTime(hour = 21, minute = 0),
            lockApplications = listOf("com.youtube.android", "Instagram"),
        )

        val payload = buildRoutineTemplateSharePayload(routine)

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals(RoutineTemplateCategory.STUDY, payload.templateCategory)
        assertEquals(RoutineTemplateRepeatDaysBucket.WEEKDAY, payload.repeatDaysBucket)
        assertEquals(RoutineTemplateTimeWindowBucket.EVENING, payload.timeWindowBucket)
        assertFalse(payload.routineNameIncluded)
        assertTrue(payload.text.contains("스탑잇 집중 루틴 템플릿"))
        assertTrue(payload.text.contains("공부 · 평일 · 저녁 2시간"))
        assertTrue(payload.text.contains("https://play.google.com/store/apps/details?id=com.uiery.keep"))
        assertFalse(payload.text.contains("Secret exam detox"))
        assertFalse(payload.text.contains("com.youtube.android"))
        assertFalse(payload.text.contains("Instagram"))
    }

    @Test
    fun buildRoutineTemplateSharePayloadCanOptInRoutineNameWithoutLeakingApps() {
        val routine = routine(
            name = "Night writing",
            repeatDays = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            startTime = LocalTime(hour = 23, minute = 30),
            endTime = LocalTime(hour = 1, minute = 0),
            lockApplications = listOf("com.example.private"),
        )

        val payload = buildRoutineTemplateSharePayload(
            routine = routine,
            includeRoutineName = true,
        )

        assertNotNull(payload)
        requireNotNull(payload)
        assertEquals(RoutineTemplateCategory.NIGHT_FOCUS, payload.templateCategory)
        assertEquals(RoutineTemplateRepeatDaysBucket.WEEKEND, payload.repeatDaysBucket)
        assertEquals(RoutineTemplateTimeWindowBucket.OVERNIGHT, payload.timeWindowBucket)
        assertTrue(payload.routineNameIncluded)
        assertTrue(payload.text.contains("Night writing"))
        assertFalse(payload.text.contains("com.example.private"))
    }

    @Test
    fun buildRoutineTemplateSharePayloadReturnsNullForInvalidRoutineWithoutRepeatDays() {
        val payload = buildRoutineTemplateSharePayload(
            routine = routine(
                repeatDays = emptyList(),
                lockApplications = listOf("com.example.private"),
            ),
        )

        assertNull(payload)
    }

    private fun routine(
        name: String = "Study routine",
        repeatDays: List<DayOfWeek> = listOf(DayOfWeek.MONDAY),
        startTime: LocalTime = LocalTime(hour = 9, minute = 0),
        endTime: LocalTime = LocalTime(hour = 10, minute = 0),
        lockApplications: List<String> = listOf("com.example.blocked"),
    ) = RoutineModel(
        id = 7L,
        name = name,
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toRepeatDaysBinary(),
        lockApplications = lockApplications,
        isEnabled = true,
    )
}
