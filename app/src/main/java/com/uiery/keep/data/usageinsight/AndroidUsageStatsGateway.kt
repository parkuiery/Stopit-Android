package com.uiery.keep.data.usageinsight

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.uiery.keep.domain.usageinsight.AppUsageDay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val NIGHT_END_HOUR = 6

class AndroidUsageStatsGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageStatsGateway {

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
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val launchableCache = mutableMapOf<String, Boolean>()
        return generateSequence(from) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(toInclusive) }
        }.flatMap { day ->
            aggregateDay(usageStatsManager, day, zone, launchableCache)
        }.toList()
    }

    private fun aggregateDay(
        usageStatsManager: UsageStatsManager,
        day: LocalDate,
        zone: ZoneId,
        launchableCache: MutableMap<String, Boolean>,
    ): List<AppUsageDay> {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nightEnd = day.atTime(NIGHT_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

        val totalMillis = mutableMapOf<String, Long>()
        val nightMillis = mutableMapOf<String, Long>()
        val launchCounts = mutableMapOf<String, Int>()
        val foregroundSince = mutableMapOf<String, Long>()
        val resumedPackages = mutableSetOf<String>()
        val closedFromDayStart = mutableSetOf<String>()

        fun closeSession(packageName: String, start: Long, end: Long) {
            if (end <= start) return
            totalMillis.merge(packageName, end - start, Long::plus)
            val nightOverlap = minOf(end, nightEnd) - maxOf(start, dayStart)
            if (nightOverlap > 0) nightMillis.merge(packageName, nightOverlap, Long::plus)
        }

        val events = usageStatsManager.queryEvents(dayStart, dayEnd)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedPackages.add(event.packageName)
                    if (foregroundSince.putIfAbsent(event.packageName, event.timeStamp) == null) {
                        launchCounts.merge(event.packageName, 1, Int::plus)
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    val start = foregroundSince.remove(event.packageName)
                    when {
                        start != null -> closeSession(event.packageName, start, event.timeStamp)
                        // orphan close인데 구간 내 RESUMED가 전혀 없었으면 자정 걸친 세션의
                        // 연속으로 보고 dayStart부터 1회만 집계한다. (launchCount 미증가)
                        // 구간 내 RESUMED가 한 번이라도 있었던 패키지의 orphan close는
                        // 같은 세션의 중복 PAUSED/STOPPED 쌍이므로 무시한다.
                        event.packageName !in resumedPackages &&
                            closedFromDayStart.add(event.packageName) ->
                            closeSession(event.packageName, dayStart, event.timeStamp)
                    }
                }
            }
        }
        foregroundSince.forEach { (packageName, start) -> closeSession(packageName, start, dayEnd) }

        return totalMillis.keys
            .filter { it != context.packageName && isLaunchable(it, launchableCache) }
            .map { packageName ->
                AppUsageDay(
                    date = day,
                    packageName = packageName,
                    totalUsage = Duration.ofMillis(totalMillis.getValue(packageName)),
                    launchCount = launchCounts[packageName] ?: 0,
                    nightUsage = Duration.ofMillis(nightMillis[packageName] ?: 0L),
                )
            }
    }

    private fun isLaunchable(packageName: String, cache: MutableMap<String, Boolean>): Boolean =
        cache.getOrPut(packageName) {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }
}
