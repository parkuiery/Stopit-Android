package com.uiery.keep.data.usageinsight

import java.time.LocalDate
import java.time.ZoneId

data class DailyUsageStat(
    val packageName: String,
    val totalForegroundMillis: Long,
    val lastUsedEpochMillis: Long,
)

fun interface DailyUsageStatsSource {
    fun query(startMillis: Long, endExclusiveMillis: Long): List<DailyUsageStat>
}

data class AppUsageAggregateDay(
    val packageName: String,
    val totalForegroundMillis: Long,
    val lastUsedEpochMillis: Long,
    val localDate: LocalDate,
)

class OnboardingUsageStatsReader(
    private val source: DailyUsageStatsSource,
    private val ownPackageName: String,
    private val excludedPackages: Set<String>,
    private val isLaunchable: (String) -> Boolean,
) {
    fun query(
        days: ClosedRange<LocalDate>,
        zoneId: ZoneId,
    ): List<AppUsageAggregateDay> {
        if (days.start > days.endInclusive) return emptyList()
        val launchableCache = mutableMapOf<String, Boolean>()
        return generateSequence(days.start) { date ->
            date.plusDays(1).takeIf { it <= days.endInclusive }
        }.flatMap { date ->
            queryDate(date, zoneId, launchableCache)
        }.toList()
    }

    private fun queryDate(
        date: LocalDate,
        zoneId: ZoneId,
        launchableCache: MutableMap<String, Boolean>,
    ): Sequence<AppUsageAggregateDay> {
        val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusiveMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val aggregates = linkedMapOf<String, AppUsageAggregateDay>()
        source.query(startMillis, endExclusiveMillis)
            .asSequence()
            .filter { stat ->
                stat.totalForegroundMillis > 0 &&
                    stat.packageName != ownPackageName &&
                    stat.packageName !in excludedPackages &&
                    launchableCache.getOrPut(stat.packageName) { isLaunchable(stat.packageName) }
            }
            .forEach { stat ->
                val previous = aggregates[stat.packageName]
                val clampedLastUsed = stat.lastUsedEpochMillis.coerceIn(
                    startMillis,
                    endExclusiveMillis - 1,
                )
                aggregates[stat.packageName] = AppUsageAggregateDay(
                    packageName = stat.packageName,
                    totalForegroundMillis = saturatedAdd(
                        previous?.totalForegroundMillis ?: 0,
                        stat.totalForegroundMillis,
                    ),
                    lastUsedEpochMillis = maxOf(
                        previous?.lastUsedEpochMillis ?: startMillis,
                        clampedLastUsed,
                    ),
                    localDate = date,
                )
            }
        return aggregates.values.asSequence()
    }

    private fun saturatedAdd(current: Long, addition: Long): Long =
        if (current > Long.MAX_VALUE - addition) Long.MAX_VALUE else current + addition
}
