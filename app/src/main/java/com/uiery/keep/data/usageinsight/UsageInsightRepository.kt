package com.uiery.keep.data.usageinsight

import com.uiery.keep.database.dao.AppUsageDailyDao
import com.uiery.keep.database.entity.AppUsageDailyEntity
import com.uiery.keep.domain.usageinsight.AppUsageDay
import com.uiery.keep.domain.usageinsight.UsageInsight
import com.uiery.keep.domain.usageinsight.UsageInsightPolicy
import com.uiery.keep.domain.usageinsight.UsageInsightType
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

private const val LOOKBACK_DAYS = 14L
private const val RETENTION_DAYS = 90L

sealed interface UsageInsightCardResult {
    data object Hidden : UsageInsightCardResult
    data object PermissionNeeded : UsageInsightCardResult
    data class Ready(val insight: UsageInsight, val appLabel: String) : UsageInsightCardResult
}

class UsageInsightRepository @Inject constructor(
    private val gateway: UsageStatsGateway,
    private val appUsageDailyDao: AppUsageDailyDao,
    private val cardStateStore: UsageInsightCardStateStore,
) {
    suspend fun currentInsightCard(today: LocalDate): UsageInsightCardResult {
        if (!gateway.isPermissionGranted()) {
            return if (cardStateStore.isPermissionCardSuppressed(today)) {
                UsageInsightCardResult.Hidden
            } else {
                UsageInsightCardResult.PermissionNeeded
            }
        }
        refreshCache(today)
        val windowStart = today.minusDays(LOOKBACK_DAYS)
        val excludedPackages = gateway.insightExcludedPackages()
        val days = appUsageDailyDao.getSince(windowStart.toString())
            .map { it.toModel() }
            .filterNot { it.packageName in excludedPackages }
        val insight = UsageInsightPolicy.evaluate(
            days = days,
            today = today,
            suppressedTypes = cardStateStore.suppressedTypes(today),
        ) ?: return UsageInsightCardResult.Hidden
        return UsageInsightCardResult.Ready(
            insight = insight,
            appLabel = gateway.appLabel(insight.packageName) ?: insight.packageName,
        )
    }

    suspend fun dismiss(type: UsageInsightType, today: LocalDate) =
        cardStateStore.recordDismissed(type, today)

    suspend fun dismissPermissionCard(today: LocalDate) =
        cardStateStore.recordPermissionCardDismissed(today)

    private suspend fun refreshCache(today: LocalDate) {
        val yesterday = today.minusDays(1)
        val windowStart = today.minusDays(LOOKBACK_DAYS)
        val latestCached = appUsageDailyDao.latestDate()?.let(LocalDate::parse)
        val collectFrom = when {
            latestCached == null -> windowStart
            !latestCached.isBefore(yesterday) -> null
            else -> maxOf(latestCached.plusDays(1), windowStart)
        }
        if (collectFrom != null) {
            val collected = gateway.queryDailyUsage(collectFrom, yesterday)
            if (collected.isNotEmpty()) {
                appUsageDailyDao.upsertAll(collected.map { it.toEntity() })
            }
        }
        appUsageDailyDao.deleteBefore(today.minusDays(RETENTION_DAYS).toString())
    }
}

private fun AppUsageDay.toEntity() = AppUsageDailyEntity(
    date = date.toString(),
    packageName = packageName,
    totalUsageMillis = totalUsage.toMillis(),
    launchCount = launchCount,
    nightUsageMillis = nightUsage.toMillis(),
)

private fun AppUsageDailyEntity.toModel() = AppUsageDay(
    date = LocalDate.parse(date),
    packageName = packageName,
    totalUsage = Duration.ofMillis(totalUsageMillis),
    launchCount = launchCount,
    nightUsage = Duration.ofMillis(nightUsageMillis),
)
