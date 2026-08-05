package com.uiery.keep.domain.websiteblocking

import com.uiery.keep.model.RoutineModel
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 루틴을 "지금 웹을 막고 있는 창"으로 옮기는 계산. 알람 수신자 안에 숨어 있으면 부팅이나
 * 앱 진입에서 같은 판정을 다시 돌릴 수 없고, 무엇보다 자정을 넘는 창의 마감을 검증할 수 없다.
 */
class RoutineWebsiteWindowsTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun aRoutineRunningRightNowCarriesItsOwnWindowEnd() {
        val now = LocalDateTime.of(2026, 8, 3, 13, 0)
        val routine = routine(
            startTime = LocalTime(9, 0),
            endTime = LocalTime(18, 0),
            repeatDays = repeatDaysOf(now.dayOfWeek),
            websites = listOf("example.com"),
        )

        val window = listOf(routine).toRoutineWebsiteWindows(now, zone).single()

        assertTrue(window.isActiveNow)
        assertEquals(setOf("example.com"), window.websites)
        assertEquals(
            now.toLocalDate().atTime(18, 0).atZone(zone).toInstant().toEpochMilli(),
            window.endEpochMillis,
        )
    }

    @Test
    fun aRoutineThatCrossesMidnightEndsTheFollowingMorningNotToday() {
        // 오늘 07:00 으로 계산하면 마감이 이미 지난 값이 되어 서비스가 뜨자마자 스스로 내려간다.
        val now = LocalDateTime.of(2026, 8, 3, 23, 30)
        val routine = routine(
            startTime = LocalTime(23, 0),
            endTime = LocalTime(7, 0),
            repeatDays = repeatDaysOf(now.dayOfWeek),
            websites = listOf("example.com"),
        )

        val window = listOf(routine).toRoutineWebsiteWindows(now, zone).single()

        assertTrue(window.isActiveNow)
        assertEquals(
            now.toLocalDate().plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli(),
            window.endEpochMillis,
        )
    }

    @Test
    fun aRoutineScheduledForAnotherDayIsNotStandingNow() {
        val now = LocalDateTime.of(2026, 8, 3, 13, 0)
        val routine = routine(
            startTime = LocalTime(9, 0),
            endTime = LocalTime(18, 0),
            repeatDays = repeatDaysOf(now.dayOfWeek.plus(1)),
            websites = listOf("example.com"),
        )

        assertFalse(listOf(routine).toRoutineWebsiteWindows(now, zone).single().isActiveNow)
    }

    @Test
    fun aDisabledRoutineIsCarriedThroughSoThePolicyCanIgnoreIt() {
        val now = LocalDateTime.of(2026, 8, 3, 13, 0)
        val routine = routine(
            startTime = LocalTime(9, 0),
            endTime = LocalTime(18, 0),
            repeatDays = repeatDaysOf(now.dayOfWeek),
            websites = listOf("example.com"),
            isEnabled = false,
        )

        assertFalse(listOf(routine).toRoutineWebsiteWindows(now, zone).single().isEnabled)
    }

    @Test
    fun aRoutineWithoutWebsitesCarriesAnEmptyTargetSet() {
        // 기존 캐시에는 이 필드가 아예 없다. null 을 그대로 흘리면 판정이 터진다.
        val now = LocalDateTime.of(2026, 8, 3, 13, 0)
        val routine = routine(
            startTime = LocalTime(9, 0),
            endTime = LocalTime(18, 0),
            repeatDays = repeatDaysOf(now.dayOfWeek),
            websites = null,
        )

        assertEquals(emptySet<String>(), listOf(routine).toRoutineWebsiteWindows(now, zone).single().websites)
    }

    private fun repeatDaysOf(vararg days: DayOfWeek): String =
        DayOfWeek.entries.joinToString("") { if (it in days) "1" else "0" }

    private fun routine(
        startTime: LocalTime,
        endTime: LocalTime,
        repeatDays: String,
        websites: List<String>?,
        isEnabled: Boolean = true,
    ) = RoutineModel(
        id = 1L,
        name = "routine",
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays,
        lockApplications = emptyList(),
        lockWebsites = websites,
        isEnabled = isEnabled,
    )
}
