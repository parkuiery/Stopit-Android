package com.uiery.keep.data.usageinsight

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUsageStatsReaderTest {

    @Test
    fun `prior seven local dates are queried individually without querying today`() {
        val today = LocalDate.of(2026, 3, 10)
        val zoneId = ZoneId.of("America/Los_Angeles")
        val source = RecordingDailyUsageStatsSource()
        val reader = reader(source)

        reader.query(today.minusDays(7)..today.minusDays(1), zoneId)

        val expectedCalls = (7L downTo 1L).map { daysAgo ->
            val date = today.minusDays(daysAgo)
            DailyUsageStatsCall(
                startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endExclusiveMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            )
        }
        assertEquals(expectedCalls, source.calls)
        val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        assertFalse(source.calls.any { it.startMillis == todayStart })
    }

    @Test
    fun `DST windows follow calendar boundaries instead of fixed twenty four hour durations`() {
        val zoneId = ZoneId.of("America/Los_Angeles")
        val springForward = LocalDate.of(2026, 3, 8)
        val fallBack = LocalDate.of(2026, 11, 1)
        val source = RecordingDailyUsageStatsSource()
        val reader = reader(source)

        reader.query(springForward..springForward, zoneId)
        reader.query(fallBack..fallBack, zoneId)

        assertEquals(23L * 60 * 60 * 1_000, source.calls[0].durationMillis)
        assertEquals(25L * 60 * 60 * 1_000, source.calls[1].durationMillis)
    }

    @Test
    fun `results clamp last use to the requested day and discard ineligible rows`() {
        val date = LocalDate.of(2026, 7, 14)
        val zoneId = ZoneId.of("Asia/Seoul")
        val dayStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val source = RecordingDailyUsageStatsSource(
            rows = listOf(
                DailyUsageStat("com.valid.before", 10_000, dayStart - 60_000),
                DailyUsageStat("com.valid.after", 20_000, dayEnd + 60_000),
                DailyUsageStat("com.zero", 0, dayStart),
                DailyUsageStat("com.negative", -1, dayStart),
                DailyUsageStat("com.uiery.keep", 30_000, dayStart),
                DailyUsageStat("com.android.settings", 30_000, dayStart),
                DailyUsageStat("com.explicitly.excluded", 30_000, dayStart),
                DailyUsageStat("com.not.launchable", 30_000, dayStart),
            ),
        )
        val reader = reader(source)

        val result = reader.query(date..date, zoneId)

        assertEquals(
            listOf(
                AppUsageAggregateDay(
                    packageName = "com.valid.before",
                    totalForegroundMillis = 10_000,
                    lastUsedEpochMillis = dayStart,
                    localDate = date,
                ),
                AppUsageAggregateDay(
                    packageName = "com.valid.after",
                    totalForegroundMillis = 20_000,
                    lastUsedEpochMillis = dayEnd - 1,
                    localDate = date,
                ),
            ),
            result,
        )
        assertTrue(result.all { it.localDate == date })
    }

    @Test
    fun `same package rows aggregate per day with saturated totals and latest clamped use`() {
        val firstDate = LocalDate.of(2026, 7, 13)
        val secondDate = firstDate.plusDays(1)
        val zoneId = ZoneId.of("Asia/Seoul")
        val firstStart = firstDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val firstEnd = secondDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val secondStart = firstEnd
        val source = DailyUsageStatsSource { startMillis, _ ->
            when (startMillis) {
                firstStart -> listOf(
                    DailyUsageStat("com.repeated", 10, firstStart - 1),
                    DailyUsageStat("com.repeated", 20, firstEnd + 1),
                    DailyUsageStat("com.repeated", -100, firstEnd - 1),
                    DailyUsageStat("com.saturated", Long.MAX_VALUE, firstStart),
                    DailyUsageStat("com.saturated", 1, firstEnd - 1),
                    DailyUsageStat("com.other", 5, firstStart + 1),
                )
                secondStart -> listOf(
                    DailyUsageStat("com.repeated", 40, secondStart + 1),
                )
                else -> emptyList()
            }
        }
        val reader = reader(source)

        val result = reader.query(firstDate..secondDate, zoneId)

        assertEquals(
            listOf(
                AppUsageAggregateDay(
                    packageName = "com.repeated",
                    totalForegroundMillis = 30,
                    lastUsedEpochMillis = firstEnd - 1,
                    localDate = firstDate,
                ),
                AppUsageAggregateDay(
                    packageName = "com.saturated",
                    totalForegroundMillis = Long.MAX_VALUE,
                    lastUsedEpochMillis = firstEnd - 1,
                    localDate = firstDate,
                ),
                AppUsageAggregateDay(
                    packageName = "com.other",
                    totalForegroundMillis = 5,
                    lastUsedEpochMillis = firstStart + 1,
                    localDate = firstDate,
                ),
                AppUsageAggregateDay(
                    packageName = "com.repeated",
                    totalForegroundMillis = 40,
                    lastUsedEpochMillis = secondStart + 1,
                    localDate = secondDate,
                ),
            ),
            result,
        )
        assertTrue(result.all { it.totalForegroundMillis > 0 })
    }

    private fun reader(source: DailyUsageStatsSource) = OnboardingUsageStatsReader(
        source = source,
        ownPackageName = "com.uiery.keep",
        excludedPackages = setOf("com.android.settings", "com.explicitly.excluded"),
        isLaunchable = { it != "com.not.launchable" },
    )
}

private data class DailyUsageStatsCall(
    val startMillis: Long,
    val endExclusiveMillis: Long,
) {
    val durationMillis: Long get() = endExclusiveMillis - startMillis
}

private class RecordingDailyUsageStatsSource(
    private val rows: List<DailyUsageStat> = emptyList(),
) : DailyUsageStatsSource {
    val calls = mutableListOf<DailyUsageStatsCall>()

    override fun query(startMillis: Long, endExclusiveMillis: Long): List<DailyUsageStat> {
        calls += DailyUsageStatsCall(startMillis, endExclusiveMillis)
        return rows
    }
}
