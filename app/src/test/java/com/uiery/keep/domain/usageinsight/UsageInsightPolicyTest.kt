package com.uiery.keep.domain.usageinsight

import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightPolicyTest {

    private val today: LocalDate = LocalDate.of(2026, 7, 3)

    private fun usageDay(
        daysAgo: Long,
        packageName: String,
        totalMinutes: Long,
        nightMinutes: Long = 0,
        launchCount: Int = 1,
    ) = AppUsageDay(
        date = today.minusDays(daysAgo),
        packageName = packageName,
        totalUsage = Duration.ofMinutes(totalMinutes),
        launchCount = launchCount,
        nightUsage = Duration.ofMinutes(nightMinutes),
    )

    @Test
    fun `최근 7일 중 3일 이상 심야 30분 사용이면 NightOwl`() {
        val days = listOf(
            usageDay(daysAgo = 1, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40),
            usageDay(daysAgo = 2, packageName = "com.youtube", totalMinutes = 80, nightMinutes = 35),
            usageDay(daysAgo = 4, packageName = "com.youtube", totalMinutes = 70, nightMinutes = 45),
        )
        val insight = UsageInsightPolicy.evaluate(days, today, emptySet())
        val nightOwl = insight as UsageInsight.NightOwl
        assertEquals("com.youtube", nightOwl.packageName)
        assertEquals(3, nightOwl.nightsCount)
        assertEquals(Duration.ofMinutes(40), nightOwl.avgNightUsage)
    }

    @Test
    fun `심야 사용이 2일뿐이면 NightOwl 아님`() {
        val days = listOf(
            usageDay(daysAgo = 1, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40),
            usageDay(daysAgo = 2, packageName = "com.youtube", totalMinutes = 80, nightMinutes = 35),
        )
        assertNull(UsageInsightPolicy.evaluate(days, today, emptySet()))
    }

    @Test
    fun `이번 주 2시간 이상이고 지난주 대비 1_5배 이상이면 WeeklySurge`() {
        val days =
            (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) } +
                (8L..14L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 10) }
        val insight = UsageInsightPolicy.evaluate(days, today, emptySet())
        val surge = insight as UsageInsight.WeeklySurge
        assertEquals("com.insta", surge.packageName)
        assertEquals(Duration.ofMinutes(210), surge.thisWeekUsage)
        assertEquals(Duration.ofMinutes(70), surge.lastWeekUsage)
    }

    @Test
    fun `지난주 사용량이 0이면 WeeklySurge 아님`() {
        val days = (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) }
        assertNull(UsageInsightPolicy.evaluate(days, today, emptySet()))
    }

    @Test
    fun `지난주 사용량이 30분 미만이면 WeeklySurge 아님`() {
        val days =
            (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) } +
                listOf(usageDay(daysAgo = 8, packageName = "com.insta", totalMinutes = 1))
        assertNull(UsageInsightPolicy.evaluate(days, today, emptySet()))
    }

    @Test
    fun `지난주 사용량이 정확히 30분이면 베이스라인 충족`() {
        val days =
            (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) } +
                listOf(usageDay(daysAgo = 8, packageName = "com.insta", totalMinutes = 30))
        val insight = UsageInsightPolicy.evaluate(days, today, emptySet())
        val surge = insight as UsageInsight.WeeklySurge
        assertEquals("com.insta", surge.packageName)
        assertEquals(Duration.ofMinutes(30), surge.lastWeekUsage)
    }

    @Test
    fun `NightOwl과 WeeklySurge가 동시에 성립하면 NightOwl 우선`() {
        val days =
            (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) } +
                (8L..14L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 10) } +
                (1L..3L).map { usageDay(daysAgo = it, packageName = "com.youtube", totalMinutes = 60, nightMinutes = 40) }
        assertTrue(UsageInsightPolicy.evaluate(days, today, emptySet()) is UsageInsight.NightOwl)
    }

    @Test
    fun `억제된 타입은 건너뛰고 다음 인사이트를 반환`() {
        val days =
            (1L..7L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 30) } +
                (8L..14L).map { usageDay(daysAgo = it, packageName = "com.insta", totalMinutes = 10) } +
                (1L..3L).map { usageDay(daysAgo = it, packageName = "com.youtube", totalMinutes = 60, nightMinutes = 40) }
        val insight = UsageInsightPolicy.evaluate(days, today, setOf(UsageInsightType.NightOwl))
        assertTrue(insight is UsageInsight.WeeklySurge)
    }

    @Test
    fun `오늘 데이터는 평가에서 제외`() {
        val days = listOf(
            usageDay(daysAgo = 0, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40),
            usageDay(daysAgo = 1, packageName = "com.youtube", totalMinutes = 90, nightMinutes = 40),
            usageDay(daysAgo = 2, packageName = "com.youtube", totalMinutes = 80, nightMinutes = 35),
        )
        assertNull(UsageInsightPolicy.evaluate(days, today, emptySet()))
    }

    @Test
    fun `NightOwl prefill은 23시30분부터 07시까지 해당 앱`() {
        val prefill = UsageInsight.NightOwl("com.youtube", 3, Duration.ofMinutes(40)).toRoutinePrefill()
        assertEquals(listOf("com.youtube"), prefill.packages)
        assertEquals(kotlinx.datetime.LocalTime(23, 30), prefill.startTime)
        assertEquals(kotlinx.datetime.LocalTime(7, 0), prefill.endTime)
    }
}
