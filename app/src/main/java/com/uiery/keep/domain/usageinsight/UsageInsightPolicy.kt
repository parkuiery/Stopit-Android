package com.uiery.keep.domain.usageinsight

import java.time.Duration
import java.time.LocalDate

object UsageInsightPolicy {
    private val NIGHT_USAGE_THRESHOLD: Duration = Duration.ofMinutes(30)
    private const val NIGHT_OWL_MIN_DAYS = 3
    private val SURGE_MIN_WEEKLY_USAGE: Duration = Duration.ofHours(2)
    private val SURGE_MIN_BASELINE: Duration = Duration.ofMinutes(30)
    private const val SURGE_RATIO_PERCENT = 150L

    fun evaluate(
        days: List<AppUsageDay>,
        today: LocalDate,
        suppressedTypes: Set<UsageInsightType>,
    ): UsageInsight? {
        val completeDays = days.filter { it.date.isBefore(today) }
        if (UsageInsightType.NightOwl !in suppressedTypes) {
            findNightOwl(completeDays, today)?.let { return it }
        }
        if (UsageInsightType.WeeklySurge !in suppressedTypes) {
            findWeeklySurge(completeDays, today)?.let { return it }
        }
        return null
    }

    private fun findNightOwl(days: List<AppUsageDay>, today: LocalDate): UsageInsight.NightOwl? {
        val weekStart = today.minusDays(7)
        return days
            .filter { !it.date.isBefore(weekStart) && it.nightUsage >= NIGHT_USAGE_THRESHOLD }
            .groupBy { it.packageName }
            .filterValues { it.size >= NIGHT_OWL_MIN_DAYS }
            .maxWithOrNull(
                compareBy(
                    { (_, nights) -> nights.size },
                    { (_, nights) -> nights.sumOf { it.nightUsage.toMillis() } },
                ),
            )
            ?.let { (packageName, nights) ->
                UsageInsight.NightOwl(
                    packageName = packageName,
                    nightsCount = nights.size,
                    avgNightUsage = Duration.ofMillis(
                        nights.sumOf { it.nightUsage.toMillis() } / nights.size,
                    ),
                )
            }
    }

    private fun findWeeklySurge(days: List<AppUsageDay>, today: LocalDate): UsageInsight.WeeklySurge? {
        val thisWeekStart = today.minusDays(7)
        val lastWeekStart = today.minusDays(14)
        val thisWeek = days
            .filter { !it.date.isBefore(thisWeekStart) }
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sumOf { it.totalUsage.toMillis() } }
        val lastWeek = days
            .filter { !it.date.isBefore(lastWeekStart) && it.date.isBefore(thisWeekStart) }
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.sumOf { it.totalUsage.toMillis() } }
        return thisWeek
            .filter { (packageName, thisWeekMillis) ->
                val lastWeekMillis = lastWeek[packageName] ?: 0L
                thisWeekMillis >= SURGE_MIN_WEEKLY_USAGE.toMillis() &&
                    lastWeekMillis >= SURGE_MIN_BASELINE.toMillis() &&
                    thisWeekMillis * 100 >= lastWeekMillis * SURGE_RATIO_PERCENT
            }
            .maxByOrNull { (_, thisWeekMillis) -> thisWeekMillis }
            ?.let { (packageName, thisWeekMillis) ->
                UsageInsight.WeeklySurge(
                    packageName = packageName,
                    thisWeekUsage = Duration.ofMillis(thisWeekMillis),
                    lastWeekUsage = Duration.ofMillis(lastWeek.getValue(packageName)),
                )
            }
    }
}
