package com.uiery.keep.service

import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class RoutineStartReevaluationPolicyTest {
    @Test
    fun nextRoutineStartReevaluationDelayReturnsDelayUntilTodayStart() {
        val delayMillis = nextRoutineStartReevaluationDelayMillis(
            routines = listOf(
                routine(
                    startTime = LocalTime(10, 5),
                    repeatDay = DayOfWeek.WEDNESDAY,
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
        )

        assertEquals(5 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextRoutineStartReevaluationDelayReturnsSoonestFutureStartAcrossRoutines() {
        val delayMillis = nextRoutineStartReevaluationDelayMillis(
            routines = listOf(
                routine(
                    id = 1L,
                    startTime = LocalTime(11, 0),
                    repeatDay = DayOfWeek.WEDNESDAY,
                ),
                routine(
                    id = 2L,
                    startTime = LocalTime(10, 15),
                    repeatDay = DayOfWeek.WEDNESDAY,
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
        )

        assertEquals(15 * 60 * 1_000L, delayMillis)
    }

    @Test
    fun nextRoutineStartReevaluationDelaySkipsDisabledAndEmptyTargetRoutines() {
        val delayMillis = nextRoutineStartReevaluationDelayMillis(
            routines = listOf(
                routine(
                    id = 1L,
                    startTime = LocalTime(10, 5),
                    repeatDay = DayOfWeek.WEDNESDAY,
                    isEnabled = false,
                ),
                routine(
                    id = 2L,
                    startTime = LocalTime(10, 10),
                    repeatDay = DayOfWeek.WEDNESDAY,
                    lockApplications = emptyList(),
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
        )

        assertNull(delayMillis)
    }

    @Test
    fun nextRoutineStartReevaluationDelayWrapsToNextRepeatDay() {
        val delayMillis = nextRoutineStartReevaluationDelayMillis(
            routines = listOf(
                routine(
                    startTime = LocalTime(9, 0),
                    repeatDay = DayOfWeek.THURSDAY,
                ),
            ),
            now = LocalDateTime.of(2026, 5, 27, 10, 0),
        )

        assertEquals(23 * 60 * 60 * 1_000L, delayMillis)
    }

    private fun routine(
        id: Long = 1L,
        startTime: LocalTime,
        repeatDay: DayOfWeek,
        isEnabled: Boolean = true,
        lockApplications: List<String> = listOf("com.example.blocked"),
    ) = RoutineModel(
        id = id,
        name = "Routine $id",
        startTime = startTime,
        endTime = LocalTime(23, 0),
        repeatDays = repeatDayBinary(repeatDay),
        lockApplications = lockApplications,
        isEnabled = isEnabled,
    )

    private fun repeatDayBinary(dayOfWeek: DayOfWeek): String = buildString {
        DayOfWeek.entries.forEach { day ->
            append(if (day == dayOfWeek) '1' else '0')
        }
    }
}
