package com.uiery.keep.data.usageinsight

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.uiery.keep.domain.usageinsight.AppUsageDay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AndroidUsageStatsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageStatsGateway {

    private val excludedPackages: Set<String> by lazy {
        val settingsPackage = Intent(Settings.ACTION_SETTINGS)
            .resolveActivity(context.packageManager)
            ?.packageName
        setOfNotNull(settingsPackage)
    }

    override fun insightExcludedPackages(): Set<String> = excludedPackages

    override fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun appLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    override fun queryDailyUsage(from: LocalDate, toInclusive: LocalDate): List<AppUsageDay> {
        if (from.isAfter(toInclusive)) return emptyList()
        val zone = ZoneId.systemDefault()
        return queryUsageReconstructions(from..toInclusive, zone)
            .flatMap { reconstruction ->
                aggregateAppUsageIntervals(
                    localDate = reconstruction.localDate,
                    zoneId = zone,
                    intervals = reconstruction.intervals,
                    acceptedInDayLaunchCounts = reconstruction.acceptedInDayLaunchCounts,
                )
            }
    }

    override fun queryOnboardingDailyAggregates(
        days: ClosedRange<LocalDate>,
        zoneId: ZoneId,
    ): List<AppUsageAggregateDay> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return OnboardingUsageStatsReader(
            source = DailyUsageStatsSource { startMillis, endExclusiveMillis ->
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startMillis,
                    endExclusiveMillis,
                ).orEmpty().map { usageStats ->
                    DailyUsageStat(
                        packageName = usageStats.packageName,
                        totalForegroundMillis = usageStats.totalTimeInForeground,
                        lastUsedEpochMillis = usageStats.lastTimeUsed,
                    )
                }
            },
            ownPackageName = context.packageName,
            excludedPackages = excludedPackages,
            isLaunchable = { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            },
        ).query(days, zoneId)
    }

    override fun queryOnboardingUsageIntervals(
        days: ClosedRange<LocalDate>,
        zoneId: ZoneId,
    ): List<AppUsageInterval> = queryUsageReconstructions(days, zoneId)
        .flatMap { it.intervals }

    private fun queryUsageReconstructions(
        days: ClosedRange<LocalDate>,
        zoneId: ZoneId,
    ): List<UsageEventReconstruction> {
        if (days.start > days.endInclusive) return emptyList()
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val launchableCache = mutableMapOf<String, Boolean>()
        val pairer = UsageEventIntervalPairer(
            ownPackageName = context.packageName,
            excludedPackages = excludedPackages,
            isLaunchable = { packageName ->
                launchableCache.getOrPut(packageName) {
                    context.packageManager.getLaunchIntentForPackage(packageName) != null
                }
            },
        )
        return generateSequence(days.start) { date ->
            date.plusDays(1).takeIf { it <= days.endInclusive }
        }.map { date ->
            val dayStartMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEndExclusiveMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            pairer.reconstruct(
                localDate = date,
                zoneId = zoneId,
                requestStartMillis = dayStartMillis,
                requestEndExclusiveMillis = dayEndExclusiveMillis,
                events = usageStatsManager.queryEvents(dayStartMillis, dayEndExclusiveMillis)
                    .toForegroundEventSamples(),
            )
        }.toList()
    }

    private fun UsageEvents.toForegroundEventSamples(): List<ForegroundEventSample> = buildList {
        val event = UsageEvents.Event()
        while (hasNextEvent()) {
            getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventType.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundEventType.Paused
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEventType.Stopped
                else -> null
            }
            if (type != null) {
                add(
                    ForegroundEventSample(
                        packageName = event.packageName,
                        timestampMillis = event.timeStamp,
                        type = type,
                    ),
                )
            }
        }
    }
}
