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

        val text = payload.buildShareText(fakeEnglishTextProvider())

        assertTrue(text.contains("StopIt focus routine template"))
        assertTrue(text.contains("Study · Weekdays · Evening 2 hours"))
        assertTrue(text.contains("https://play.google.com/store/apps/details?id=com.uiery.keep"))
        assertFalse(text.contains("Secret exam detox"))
        assertFalse(text.contains("com.youtube.android"))
        assertFalse(text.contains("Instagram"))
    }

    @Test
    fun buildRoutineTemplateSharePayloadUsesTextProviderForLocaleSpecificLabelsAndTemplate() {
        val routine = routine(
            name = "업무 루틴",
            repeatDays = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            startTime = LocalTime(hour = 9, minute = 0),
            endTime = LocalTime(hour = 10, minute = 30),
        )

        val payload = buildRoutineTemplateSharePayload(routine)

        assertNotNull(payload)
        requireNotNull(payload)
        val text = payload.buildShareText(fakeSpanishTextProvider())
        assertTrue(text.contains("Plantilla de rutina de enfoque de StopIt"))
        assertTrue(text.contains("Trabajo · Fin de semana · Mañana 1 hora 30 minutos"))
        assertTrue(text.contains("Pausa apps cuando necesitas enfocarte."))
        assertFalse(text.contains("업무"))
        assertFalse(text.contains("평일"))
        assertFalse(text.contains("시간"))
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
        val text = payload.buildShareText(fakeEnglishTextProvider())
        assertTrue(text.contains("Night writing"))
        assertFalse(text.contains("com.example.private"))
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

    private fun fakeEnglishTextProvider() =
        FakeRoutineTemplateShareTextProvider(
            title = "StopIt focus routine template",
            cta = "Pause apps when you need focus.",
            categoryLabels = mapOf(
                RoutineTemplateCategory.STUDY to "Study",
                RoutineTemplateCategory.WORK to "Work",
                RoutineTemplateCategory.NIGHT_FOCUS to "Night focus",
                RoutineTemplateCategory.CUSTOM to "Focus",
            ),
            repeatLabels = mapOf(
                RoutineTemplateRepeatDaysBucket.WEEKDAY to "Weekdays",
                RoutineTemplateRepeatDaysBucket.WEEKEND to "Weekend",
                RoutineTemplateRepeatDaysBucket.DAILY to "Every day",
                RoutineTemplateRepeatDaysBucket.CUSTOM_DAYS to "Selected days",
                RoutineTemplateRepeatDaysBucket.NONE to "No days",
            ),
            timeWindowLabels = mapOf(
                RoutineTemplateTimeWindowBucket.MORNING to "Morning",
                RoutineTemplateTimeWindowBucket.AFTERNOON to "Afternoon",
                RoutineTemplateTimeWindowBucket.EVENING to "Evening",
                RoutineTemplateTimeWindowBucket.NIGHT to "Night",
                RoutineTemplateTimeWindowBucket.OVERNIGHT to "Overnight",
                RoutineTemplateTimeWindowBucket.CUSTOM_WINDOW to "Custom time",
            ),
            durationFormatter = { minutes ->
                when (minutes) {
                    120L -> "2 hours"
                    else -> "$minutes minutes"
                }
            },
        )

    private fun fakeSpanishTextProvider() =
        FakeRoutineTemplateShareTextProvider(
            title = "Plantilla de rutina de enfoque de StopIt",
            cta = "Pausa apps cuando necesitas enfocarte.",
            categoryLabels = mapOf(
                RoutineTemplateCategory.STUDY to "Estudio",
                RoutineTemplateCategory.WORK to "Trabajo",
                RoutineTemplateCategory.NIGHT_FOCUS to "Enfoque nocturno",
                RoutineTemplateCategory.CUSTOM to "Enfoque",
            ),
            repeatLabels = mapOf(
                RoutineTemplateRepeatDaysBucket.WEEKDAY to "Entre semana",
                RoutineTemplateRepeatDaysBucket.WEEKEND to "Fin de semana",
                RoutineTemplateRepeatDaysBucket.DAILY to "Cada día",
                RoutineTemplateRepeatDaysBucket.CUSTOM_DAYS to "Días elegidos",
                RoutineTemplateRepeatDaysBucket.NONE to "Sin días",
            ),
            timeWindowLabels = mapOf(
                RoutineTemplateTimeWindowBucket.MORNING to "Mañana",
                RoutineTemplateTimeWindowBucket.AFTERNOON to "Tarde",
                RoutineTemplateTimeWindowBucket.EVENING to "Noche",
                RoutineTemplateTimeWindowBucket.NIGHT to "Madrugada",
                RoutineTemplateTimeWindowBucket.OVERNIGHT to "Toda la noche",
                RoutineTemplateTimeWindowBucket.CUSTOM_WINDOW to "Horario elegido",
            ),
            durationFormatter = { minutes ->
                when (minutes) {
                    90L -> "1 hora 30 minutos"
                    else -> "$minutes minutos"
                }
            },
        )

    private class FakeRoutineTemplateShareTextProvider(
        private val title: String,
        private val cta: String,
        private val categoryLabels: Map<RoutineTemplateCategory, String>,
        private val repeatLabels: Map<RoutineTemplateRepeatDaysBucket, String>,
        private val timeWindowLabels: Map<RoutineTemplateTimeWindowBucket, String>,
        private val durationFormatter: (Long) -> String,
    ) : RoutineTemplateShareTextProvider {
        override fun title(): String = title

        override fun categoryLabel(category: RoutineTemplateCategory): String = categoryLabels.getValue(category)

        override fun repeatDaysLabel(bucket: RoutineTemplateRepeatDaysBucket): String = repeatLabels.getValue(bucket)

        override fun timeWindowLabel(bucket: RoutineTemplateTimeWindowBucket): String = timeWindowLabels.getValue(bucket)

        override fun durationText(totalMinutes: Long): String = durationFormatter(totalMinutes)

        override fun callToAction(): String = cta
    }
}
