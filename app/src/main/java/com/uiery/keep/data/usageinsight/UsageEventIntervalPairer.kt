package com.uiery.keep.data.usageinsight

import com.uiery.keep.domain.usageinsight.AppUsageDay
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

enum class ForegroundEventType {
    Resumed,
    Paused,
    Stopped,
}

data class ForegroundEventSample(
    val packageName: String,
    val timestampMillis: Long,
    val type: ForegroundEventType,
)

data class AppUsageInterval(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
    val localDate: LocalDate,
    val countsAsLaunch: Boolean,
)

/** Pairs framework-free foreground events using the same rules as the Home daily summary. */
class UsageEventIntervalPairer(
    private val ownPackageName: String,
    private val excludedPackages: Set<String>,
    private val isLaunchable: (String) -> Boolean,
) {
    fun pair(
        localDate: LocalDate,
        zoneId: ZoneId,
        requestStartMillis: Long,
        requestEndExclusiveMillis: Long,
        events: List<ForegroundEventSample>,
    ): List<AppUsageInterval> {
        val dayStartMillis = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndExclusiveMillis = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val clampedRequestStart = maxOf(requestStartMillis, dayStartMillis)
        val clampedRequestEnd = minOf(requestEndExclusiveMillis, dayEndExclusiveMillis)
        if (clampedRequestStart >= clampedRequestEnd) return emptyList()

        val launchableCache = mutableMapOf<String, Boolean>()
        fun isIncluded(packageName: String): Boolean =
            packageName != ownPackageName &&
                packageName !in excludedPackages &&
                launchableCache.getOrPut(packageName) { isLaunchable(packageName) }

        val foregroundSince = mutableMapOf<String, Long>()
        val resumedPackages = mutableSetOf<String>()
        val closedFromDayStart = mutableSetOf<String>()
        val intervals = mutableListOf<AppUsageInterval>()

        fun addInterval(
            packageName: String,
            startMillis: Long,
            endMillis: Long,
            countsAsLaunch: Boolean,
        ) {
            val clampedStart = maxOf(startMillis, clampedRequestStart)
            val clampedEnd = minOf(endMillis, clampedRequestEnd)
            if (clampedEnd <= clampedStart) return
            intervals += AppUsageInterval(
                packageName = packageName,
                startMillis = clampedStart,
                endMillis = clampedEnd,
                localDate = localDate,
                countsAsLaunch = countsAsLaunch,
            )
        }

        events.asSequence()
            .filter { it.timestampMillis >= dayStartMillis && it.timestampMillis < dayEndExclusiveMillis }
            .filter { isIncluded(it.packageName) }
            .forEach { event ->
                when (event.type) {
                    ForegroundEventType.Resumed -> {
                        resumedPackages += event.packageName
                        foregroundSince.putIfAbsent(event.packageName, event.timestampMillis)
                    }

                    ForegroundEventType.Paused,
                    ForegroundEventType.Stopped,
                    -> {
                        val startMillis = foregroundSince.remove(event.packageName)
                        when {
                            startMillis != null -> addInterval(
                                packageName = event.packageName,
                                startMillis = startMillis,
                                endMillis = event.timestampMillis,
                                countsAsLaunch = true,
                            )

                            event.packageName !in resumedPackages &&
                                closedFromDayStart.add(event.packageName) -> addInterval(
                                packageName = event.packageName,
                                startMillis = dayStartMillis,
                                endMillis = event.timestampMillis,
                                countsAsLaunch = false,
                            )
                        }
                    }
                }
            }

        foregroundSince.forEach { (packageName, startMillis) ->
            addInterval(
                packageName = packageName,
                startMillis = startMillis,
                endMillis = dayEndExclusiveMillis,
                countsAsLaunch = true,
            )
        }

        return intervals.sortedWith(
            compareBy<AppUsageInterval> { it.startMillis }
                .thenBy { it.packageName }
                .thenBy { it.endMillis },
        )
    }
}

internal fun aggregateAppUsageIntervals(
    localDate: LocalDate,
    zoneId: ZoneId,
    intervals: List<AppUsageInterval>,
): List<AppUsageDay> {
    val dayStartMillis = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val nightEndMillis = localDate.atTime(6, 0).atZone(zoneId).toInstant().toEpochMilli()
    return intervals
        .filter { it.localDate == localDate }
        .groupBy { it.packageName }
        .map { (packageName, packageIntervals) ->
            AppUsageDay(
                date = localDate,
                packageName = packageName,
                totalUsage = Duration.ofMillis(
                    packageIntervals.sumOf { it.endMillis - it.startMillis },
                ),
                launchCount = packageIntervals.count { it.countsAsLaunch },
                nightUsage = Duration.ofMillis(
                    packageIntervals.sumOf { interval ->
                        (minOf(interval.endMillis, nightEndMillis) -
                            maxOf(interval.startMillis, dayStartMillis)).coerceAtLeast(0)
                    },
                ),
            )
        }
}
