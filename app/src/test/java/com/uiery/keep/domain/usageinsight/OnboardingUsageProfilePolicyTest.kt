package com.uiery.keep.domain.usageinsight

import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsagePatternType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUsageProfilePolicyTest {
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val firstDay = LocalDate.of(2026, 7, 7)
    private val allDays = DayOfWeek.entries.map { it.value }.toSet()

    @Test
    fun `coverage buckets preserve zero sparse partial and complete evidence`() {
        assertEquals(UsageCoverageBucket.Zero, OnboardingUsageProfilePolicy.coverageBucket(0))
        assertEquals(UsageCoverageBucket.OneTwo, OnboardingUsageProfilePolicy.coverageBucket(1))
        assertEquals(UsageCoverageBucket.OneTwo, OnboardingUsageProfilePolicy.coverageBucket(2))
        assertEquals(UsageCoverageBucket.ThreeSix, OnboardingUsageProfilePolicy.coverageBucket(3))
        assertEquals(UsageCoverageBucket.ThreeSix, OnboardingUsageProfilePolicy.coverageBucket(6))
        assertEquals(UsageCoverageBucket.Seven, OnboardingUsageProfilePolicy.coverageBucket(7))
    }

    @Test
    fun `usage coverage is independent from candidate event coverage and divides average`() {
        val result = evaluate(
            aggregates = aggregates("com.video", 3, 30),
            intervals = listOf(interval("com.video", 0, 20, 0, 20, 50)),
        ).ready()

        assertEquals(3, result.usageCoverageDays)
        assertEquals(1, result.eventCoverageDays)
        assertEquals(30, result.averageDailyMinutes)
        assertEquals(UsageDataQuality.UsageOnly, result.dataQuality)
        assertEquals(UsagePatternType.TopApp, result.patternType)
        assertEquals(21 * 60, result.suggestedStartMinutes)
    }

    @Test
    fun `average below fifteen minutes excludes a candidate`() {
        val result = evaluate(aggregates = aggregates("com.short", 3, 14))

        assertEquals(InsufficientReason.NoCandidate, result.insufficient().reason)
    }

    @Test
    fun `candidate ranking uses total days recency then package name`() {
        val day = firstDay
        fun selected(vararg candidate: OnboardingUsageAggregate): String = evaluate(
            aggregates = candidate.toList() + listOf(
                aggregate("coverage", day.plusDays(1), 1, 1),
                aggregate("coverage", day.plusDays(2), 1, 1),
            ),
        ).ready().packageName

        assertEquals(
            "com.higher-total",
            selected(
                aggregate("com.higher-total", day, 101, 1),
                aggregate("com.lower-total", day, 100, 100),
            ),
        )
        assertEquals(
            "com.more-days",
            selected(
                aggregate("com.more-days", day, 50, 1),
                aggregate("com.more-days", day.plusDays(1), 50, 1),
                aggregate("com.one-day", day, 100, 100),
            ),
        )
        assertEquals(
            "com.recent",
            selected(
                aggregate("com.recent", day, 100, 20),
                aggregate("com.old", day, 100, 10),
            ),
        )
        assertEquals(
            "com.alpha",
            selected(
                aggregate("com.beta", day, 100, 20),
                aggregate("com.alpha", day, 100, 20),
            ),
        )
    }

    @Test
    fun `full profile distributes intervals over half-hour buckets and uses deterministic peak ties`() {
        val aggregates = aggregates("com.video", 3, 60)
        val intervals = listOf(
            interval("com.video", 0, 20, 10, 20, 50),
            interval("com.video", 1, 20, 10, 20, 50),
            interval("com.video", 2, 20, 10, 20, 50),
            interval("com.video", 2, 21, 0, 21, 20),
        )

        val result = evaluate(aggregates, intervals).ready()

        assertEquals(UsageDataQuality.Full, result.dataQuality)
        assertEquals(20 * 60 + 30, result.suggestedStartMinutes)
        assertEquals(UsagePatternType.PeakWindow, result.patternType)
        assertEquals(3, result.eventCoverageDays)
    }

    @Test
    fun `peak tie prefers distinct days then latest observation then earlier bucket`() {
        val aggregates = aggregates("com.video", 3, 90)
        val distinctDaysWin = listOf(
            interval("com.video", 0, 18, 0, 18, 30),
            interval("com.video", 1, 18, 0, 18, 30),
            interval("com.video", 2, 19, 0, 20, 0),
        )
        assertEquals(18 * 60, evaluate(aggregates, distinctDaysWin).ready().suggestedStartMinutes)

        val recentWins = listOf(
            interval("com.video", 0, 18, 0, 18, 30),
            interval("com.video", 0, 19, 0, 19, 30),
            interval("com.video", 1, 20, 0, 20, 1),
            interval("com.video", 1, 21, 0, 21, 1),
            interval("com.video", 2, 22, 0, 22, 30),
        )
        assertEquals(22 * 60, evaluate(aggregates, recentWins).ready().suggestedStartMinutes)

        val tiedEvidence = listOf(
            UsageTimeBucketEvidence(19 * 60, 30, 3, 99),
            UsageTimeBucketEvidence(18 * 60, 30, 3, 99),
        )
        assertEquals(18 * 60, OnboardingUsageProfilePolicy.selectPeakStart(tiedEvidence))
    }

    @Test
    fun `night includes 2200 through 0530 boundaries`() {
        listOf(22 * 60, 23 * 60 + 30, 0, 5 * 60 + 30).forEach { startMinutes ->
            val result = evaluate(
                aggregates("com.video", 3, 60),
                intervalsAtBucket("com.video", startMinutes),
            ).ready()
            assertEquals(startMinutes, result.suggestedStartMinutes)
            assertEquals(UsagePatternType.Night, result.patternType)
        }
        val daytime = evaluate(
            aggregates("com.video", 3, 60),
            intervalsAtBucket("com.video", 6 * 60),
        ).ready()
        assertEquals(UsagePatternType.PeakWindow, daytime.patternType)
    }

    @Test
    fun `midnight and DST intervals clamp to calendar days without fixed day assumptions`() {
        val newYork = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2026, 3, 8)
        val dayStart = spring.atStartOfDay(newYork).toInstant().toEpochMilli()
        val result = OnboardingUsageProfilePolicy.evaluate(
            aggregates = (0..2).map { offset ->
                OnboardingUsageAggregate("com.video", Duration.ofHours(1).toMillis(), dayStart, spring.plusDays(offset.toLong()))
            },
            intervals = (0..2).map { offset ->
                val date = spring.plusDays(offset.toLong())
                OnboardingUsageInterval(
                    packageName = "com.video",
                    startMillis = if (offset == 0) {
                        dayStart - Duration.ofHours(1).toMillis()
                    } else {
                        date.atStartOfDay(newYork).toInstant().toEpochMilli()
                    },
                    endMillis = date.atTime(0, 30).atZone(newYork).toInstant().toEpochMilli(),
                    localDate = date,
                )
            },
            appLabels = mapOf("com.video" to "Video"),
            enabledRoutines = emptyList(),
            goalDefaultStartMinutes = 21 * 60,
            proposedRepeatDays = allDays,
            zoneId = newYork,
        ).ready()

        assertEquals(0, result.suggestedStartMinutes)
        assertEquals(UsagePatternType.Night, result.patternType)
    }

    @Test
    fun `spring-forward gap uses real calendar buckets and skips nonexistent local time`() {
        val newYork = ZoneId.of("America/New_York")
        val transitionDay = LocalDate.of(2026, 3, 8)

        val result = evaluateTransition(
            zone = newYork,
            transitionDay = transitionDay,
            transitionStart = transitionDay.atTime(1, 30).atZone(newYork).toInstant().toEpochMilli(),
            transitionEnd = transitionDay.atTime(3, 30).atZone(newYork).toInstant().toEpochMilli(),
        )

        assertEquals(3 * 60, result.suggestedStartMinutes)
        assertEquals(UsagePatternType.Night, result.patternType)
    }

    @Test
    fun `fall-back repeated hour accumulates both occurrences before choosing peak`() {
        val newYork = ZoneId.of("America/New_York")
        val transitionDay = LocalDate.of(2026, 11, 1)

        val result = evaluateTransition(
            zone = newYork,
            transitionDay = transitionDay,
            transitionStart = transitionDay.atTime(1, 0).atZone(newYork).toInstant().toEpochMilli(),
            transitionEnd = transitionDay.atTime(2, 0).atZone(newYork).toInstant().toEpochMilli(),
        )

        assertEquals(1 * 60 + 30, result.suggestedStartMinutes)
        assertEquals(UsagePatternType.Night, result.patternType)
    }

    @Test
    fun `exact quality rules return typed insufficient usage-only and full`() {
        assertEquals(
            InsufficientReason.InsufficientUsageCoverage,
            evaluate(aggregates("com.video", 2, 60)).insufficient().reason,
        )
        assertEquals(
            UsageDataQuality.UsageOnly,
            evaluate(aggregates("com.video", 3, 60), intervalsAtBucket("com.video", 20 * 60, 2)).ready().dataQuality,
        )
        assertEquals(
            UsageDataQuality.Full,
            evaluate(aggregates("com.video", 3, 60), intervalsAtBucket("com.video", 20 * 60, 3)).ready().dataQuality,
        )
    }

    @Test
    fun `only full routine coverage of package time and every proposed day excludes candidate`() {
        val aggregates = aggregates("com.video", 3, 60)
        val intervals = intervalsAtBucket("com.video", 23 * 60 + 30)
        val partialTime = routine("com.video", 23 * 60 + 45, 30, allDays)
        val someWeekdays = routine("com.video", 23 * 60, 60, setOf(1, 2, 3))
        val unrelated = routine("com.other", 23 * 60, 60, allDays)

        listOf(partialTime, someWeekdays, unrelated).forEach { coverage ->
            assertTrue(evaluate(aggregates, intervals, listOf(coverage)) is OnboardingUsageProfileResult.Ready)
        }
        val fullyCovered = routine("com.video", 23 * 60, 60, allDays)
        assertEquals(
            InsufficientReason.NoCandidate,
            evaluate(aggregates, intervals, listOf(fullyCovered)).insufficient().reason,
        )
    }

    @Test
    fun `usage-only overlap is checked at goal default before app selection`() {
        val coverage = routine("com.video", 21 * 60, 22 * 60, allDays)

        val result = evaluate(
            aggregates = aggregates("com.video", 3, 60),
            routines = listOf(coverage),
            goalDefaultStartMinutes = 21 * 60,
        )

        assertEquals(InsufficientReason.NoCandidate, result.insufficient().reason)
    }

    private fun evaluate(
        aggregates: List<OnboardingUsageAggregate>,
        intervals: List<OnboardingUsageInterval> = emptyList(),
        routines: List<EnabledRoutineCoverage> = emptyList(),
        goalDefaultStartMinutes: Int = 21 * 60,
    ): OnboardingUsageProfileResult = OnboardingUsageProfilePolicy.evaluate(
        aggregates = aggregates,
        intervals = intervals,
        appLabels = aggregates.associate { it.packageName to it.packageName.removePrefix("com.") },
        enabledRoutines = routines,
        goalDefaultStartMinutes = goalDefaultStartMinutes,
        proposedRepeatDays = allDays,
        zoneId = zoneId,
    )

    private fun evaluateTransition(
        zone: ZoneId,
        transitionDay: LocalDate,
        transitionStart: Long,
        transitionEnd: Long,
    ): OnboardingUsageProfile {
        val evidenceDays = listOf(transitionDay, transitionDay.plusDays(1), transitionDay.plusDays(2))
        val aggregates = evidenceDays.map { date ->
            OnboardingUsageAggregate(
                packageName = "com.video",
                totalForegroundMillis = Duration.ofHours(1).toMillis(),
                lastUsedEpochMillis = date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
                localDate = date,
            )
        }
        val intervals = buildList {
            add(OnboardingUsageInterval("com.video", transitionStart, transitionEnd, transitionDay))
            evidenceDays.drop(1).forEach { date ->
                val start = date.atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
                add(OnboardingUsageInterval("com.video", start, start + 1, date))
            }
        }
        return OnboardingUsageProfilePolicy.evaluate(
            aggregates = aggregates,
            intervals = intervals,
            appLabels = mapOf("com.video" to "Video"),
            enabledRoutines = emptyList(),
            goalDefaultStartMinutes = 21 * 60,
            proposedRepeatDays = allDays,
            zoneId = zone,
        ).ready()
    }

    private fun aggregates(packageName: String, days: Int, dailyMinutes: Long) =
        (0 until days).map { offset -> aggregate(packageName, firstDay.plusDays(offset.toLong()), dailyMinutes, offset.toLong()) }

    private fun aggregate(packageName: String, date: LocalDate, minutes: Long, lastMinute: Long) =
        OnboardingUsageAggregate(
            packageName = packageName,
            totalForegroundMillis = Duration.ofMinutes(minutes).toMillis(),
            lastUsedEpochMillis = date.atStartOfDay(zoneId).plusMinutes(lastMinute).toInstant().toEpochMilli(),
            localDate = date,
        )

    private fun intervalsAtBucket(packageName: String, startMinutes: Int, days: Int = 3) =
        (0 until days).map { offset ->
            val start = LocalTime.of(startMinutes / 60, startMinutes % 60)
            interval(packageName, offset, start.hour, start.minute, start.plusMinutes(30).hour, start.plusMinutes(30).minute)
        }

    private fun interval(packageName: String, dayOffset: Int, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
        firstDay.plusDays(dayOffset.toLong()).let { date ->
            val start = date.atTime(startHour, startMinute).atZone(zoneId).toInstant().toEpochMilli()
            val endDate = if (endHour * 60 + endMinute <= startHour * 60 + startMinute) date.plusDays(1) else date
            OnboardingUsageInterval(
                packageName = packageName,
                startMillis = start,
                endMillis = endDate.atTime(endHour, endMinute).atZone(zoneId).toInstant().toEpochMilli(),
                localDate = date,
            )
        }

    private fun routine(packageName: String, start: Int, end: Int, days: Set<Int>) = EnabledRoutineCoverage(
        packageNames = setOf(packageName),
        startMinutes = start,
        endMinutes = end,
        repeatDays = days,
    )

    private fun OnboardingUsageProfileResult.ready() = (this as OnboardingUsageProfileResult.Ready).profile
    private fun OnboardingUsageProfileResult.insufficient() = this as OnboardingUsageProfileResult.Insufficient
}
