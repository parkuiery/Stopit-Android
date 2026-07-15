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
        return source.query(startMillis, endExclusiveMillis)
            .asSequence()
            .filter { stat ->
                stat.totalForegroundMillis > 0 &&
                    stat.packageName != ownPackageName &&
                    stat.packageName !in excludedPackages &&
                    launchableCache.getOrPut(stat.packageName) { isLaunchable(stat.packageName) }
            }
            .map { stat ->
                AppUsageAggregateDay(
                    packageName = stat.packageName,
                    totalForegroundMillis = stat.totalForegroundMillis,
                    lastUsedEpochMillis = stat.lastUsedEpochMillis.coerceIn(
                        startMillis,
                        endExclusiveMillis - 1,
                    ),
                    localDate = date,
                )
            }
    }
}
