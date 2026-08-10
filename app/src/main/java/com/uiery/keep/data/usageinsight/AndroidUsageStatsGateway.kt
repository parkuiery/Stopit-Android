package com.uiery.keep.data.usageinsight

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.uiery.keep.BuildConfig
import com.uiery.keep.appselection.BlockExemptPackageProvider
import com.uiery.keep.domain.usageinsight.AppUsageDay
import com.uiery.keep.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AndroidUsageStatsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockExemptPackageProvider: BlockExemptPackageProvider,
) : UsageStatsGateway {

    /**
     * Insight and onboarding recommendations feed straight into blocking targets, so anything the
     * blocker refuses to act on must never be recommended. Without this a user whose most-used app
     * is the dialer or the messaging app gets a first promise that can never fire.
     *
     * Not cached here. The provider already bounds how often it re-resolves, and a second cache on
     * top of it would pin the very first answer for the life of the process — which is what made a
     * user who changed their default dialer keep getting it recommended as a blocking target.
     */
    override fun insightExcludedPackages(): Set<String> =
        blockExemptPackageProvider.exemptPackages().all

    override fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        val granted = mode == AppOpsManager.MODE_ALLOWED
        debugLog { "[permission] package=${context.packageName}, appOpsMode=$mode, granted=$granted" }
        return granted
    }

    override fun appLabel(packageName: String): String? {
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
        debugLog { "[app_label] package=$packageName, label=$label" }
        return label
    }

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
        if (days.start > days.endInclusive) return emptyList()
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val rangeStartMillis = days.start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val rangeEndExclusiveMillis = days.endInclusive.plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            rangeStartMillis,
            rangeEndExclusiveMillis,
        ).orEmpty()
        debugLog {
            "[usage_stats_query] start=$rangeStartMillis, endExclusive=$rangeEndExclusiveMillis, " +
                "zone=$zoneId, resultCount=${usageStats.size}"
        }
        if (BuildConfig.DEBUG) usageStats.forEachIndexed { index, stat ->
            debugLog {
                "[usage_stats_raw] index=$index, package=${stat.packageName}, " +
                    "firstTimestamp=${stat.firstTimeStamp}, lastTimestamp=${stat.lastTimeStamp}, " +
                    "lastTimeUsed=${stat.lastTimeUsed}, " +
                    "lastTimeVisible=${stat.lastTimeVisible}, " +
                    "lastForegroundServiceUsed=${stat.lastTimeForegroundServiceUsed}, " +
                    "totalForegroundMillis=${stat.totalTimeInForeground}, " +
                    "totalVisibleMillis=${stat.totalTimeVisible}, " +
                    "totalForegroundServiceMillis=${stat.totalTimeForegroundServiceUsed}"
            }
        }
        val statsByUsageDate = usageStats.mapNotNull { stat ->
            val usageDate = stat.lastTimeUsed
                .takeIf { it in rangeStartMillis until rangeEndExclusiveMillis }
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
                ?.takeIf { it in days }
            if (usageDate == null) {
                debugLog {
                    "[usage_stats_ignored] package=${stat.packageName}, " +
                        "lastTimeUsed=${stat.lastTimeUsed}, reason=outside_requested_dates"
                }
                null
            } else {
                debugLog {
                    "[usage_stats_mapped] date=$usageDate, package=${stat.packageName}, " +
                        "firstTimestamp=${stat.firstTimeStamp}, lastTimestamp=${stat.lastTimeStamp}, " +
                        "totalForegroundMillis=${stat.totalTimeInForeground}"
                }
                usageDate to DailyUsageStat(
                    packageName = stat.packageName,
                    totalForegroundMillis = stat.totalTimeInForeground,
                    lastUsedEpochMillis = stat.lastTimeUsed,
                )
            }
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        val aggregates = OnboardingUsageStatsReader(
            source = DailyUsageStatsSource { startMillis, endExclusiveMillis ->
                val usageDate = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
                val dailyStats = statsByUsageDate[usageDate].orEmpty()
                debugLog {
                    "[usage_stats_day_source] date=$usageDate, start=$startMillis, " +
                        "endExclusive=$endExclusiveMillis, resultCount=${dailyStats.size}"
                }
                dailyStats
            },
            ownPackageName = context.packageName,
            excludedPackages = insightExcludedPackages(),
            isLaunchable = { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            },
        ).query(days, zoneId)
        if (BuildConfig.DEBUG) aggregates.forEach { aggregate ->
            debugLog {
                "[usage_stats_daily] date=${aggregate.localDate}, package=${aggregate.packageName}, " +
                    "totalForegroundMillis=${aggregate.totalForegroundMillis}, " +
                    "lastUsed=${aggregate.lastUsedEpochMillis}"
            }
        }
        return aggregates
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
            excludedPackages = insightExcludedPackages(),
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
            debugLog {
                "[usage_events_query] date=$date, start=$dayStartMillis, " +
                    "endExclusive=$dayEndExclusiveMillis, zone=$zoneId"
            }
            val samples = usageStatsManager.queryEvents(dayStartMillis, dayEndExclusiveMillis)
                .toForegroundEventSamples()
            val reconstruction = pairer.reconstruct(
                localDate = date,
                zoneId = zoneId,
                requestStartMillis = dayStartMillis,
                requestEndExclusiveMillis = dayEndExclusiveMillis,
                events = samples,
            )
            debugLog {
                "[usage_events_daily] date=$date, foregroundSampleCount=${samples.size}, " +
                    "intervalCount=${reconstruction.intervals.size}, " +
                    "launchCounts=${reconstruction.acceptedInDayLaunchCounts}"
            }
            if (BuildConfig.DEBUG) reconstruction.intervals.forEachIndexed { index, interval ->
                debugLog {
                    "[usage_interval] index=$index, date=${interval.localDate}, " +
                        "package=${interval.packageName}, start=${interval.startMillis}, " +
                        "end=${interval.endMillis}, durationMillis=${interval.endMillis - interval.startMillis}, " +
                        "countsAsLaunch=${interval.countsAsLaunch}"
                }
            }
            reconstruction
        }.toList()
    }

    private fun UsageEvents.toForegroundEventSamples(): List<ForegroundEventSample> = buildList {
        val event = UsageEvents.Event()
        var index = 0
        while (hasNextEvent()) {
            getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventType.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundEventType.Paused
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEventType.Stopped
                else -> null
            }
            debugLog {
                val extras = if (Build.VERSION.SDK_INT >= 35) event.extras else null
                "[usage_event_raw] index=$index, package=${event.packageName}, " +
                    "class=${event.className}, timestamp=${event.timeStamp}, " +
                    "eventType=${event.eventType}, foregroundType=$type, " +
                    "configuration=${event.configuration}, shortcutId=${event.shortcutId}, " +
                    "standbyBucket=${event.appStandbyBucket}, extras=$extras"
            }
            index += 1
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
        debugLog { "[usage_events_result] rawEventCount=$index, foregroundSampleCount=$size" }
    }

    private inline fun debugLog(message: () -> String) {
        if (BuildConfig.DEBUG) AppLogger.debug(LOG_TAG, message())
    }

    private companion object {
        const val LOG_TAG = "UsageStatsDebug"
    }
}
