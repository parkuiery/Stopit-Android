package com.uiery.keep.domain.usageinsight

import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType
import java.time.Instant
import java.time.ZoneId

object OnboardingUsageProfilePolicy {
    private const val MINIMUM_AVERAGE_MILLIS = 15L * 60 * 1_000
    private const val PROMISE_DURATION_MINUTES = 30
    private const val MINIMUM_COVERAGE_DAYS = 3
    private const val MINUTES_PER_DAY = 24 * 60

    fun coverageBucket(days: Int): UsageCoverageBucket = when (days.coerceAtLeast(0)) {
        0 -> UsageCoverageBucket.Zero
        in 1..2 -> UsageCoverageBucket.OneTwo
        in 3..6 -> UsageCoverageBucket.ThreeSix
        else -> UsageCoverageBucket.Seven
    }

    internal fun selectPeakStart(evidence: List<UsageTimeBucketEvidence>): Int? = evidence
        .sortedWith(
            compareByDescending<UsageTimeBucketEvidence> { it.totalMillis }
                .thenByDescending { it.distinctDays }
                .thenByDescending { it.lastObservedEpochMillis }
                .thenBy { it.startMinutes },
        )
        .firstOrNull()
        ?.startMinutes

    fun evaluate(
        aggregates: List<OnboardingUsageAggregate>,
        intervals: List<OnboardingUsageInterval>,
        appLabels: Map<String, String>,
        enabledRoutines: List<EnabledRoutineCoverage>,
        goalDefaultStartMinutes: Int,
        proposedRepeatDays: Set<Int>,
        zoneId: ZoneId,
    ): OnboardingUsageProfileResult {
        val usableAggregates = aggregates.filter { it.totalForegroundMillis > 0 }
        val usageCoverageDays = usableAggregates.map { it.localDate }.distinct().size
        if (usageCoverageDays < MINIMUM_COVERAGE_DAYS) {
            return OnboardingUsageProfileResult.Insufficient(
                usageCoverageDays = usageCoverageDays,
                eventCoverageDays = intervals.validDays(zoneId),
                reason = InsufficientReason.InsufficientUsageCoverage,
            )
        }

        val candidates = usableAggregates
            .groupBy { it.packageName }
            .map { (packageName, observations) ->
                Candidate(
                    packageName = packageName,
                    totalMillis = observations.fold(0L) { total, observation ->
                        saturatedAdd(total, observation.totalForegroundMillis)
                    },
                    distinctUsageDays = observations.map { it.localDate }.distinct().size,
                    lastUsedEpochMillis = observations.maxOf { it.lastUsedEpochMillis },
                )
            }
            .sortedWith(
                compareByDescending<Candidate> { it.totalMillis }
                    .thenByDescending { it.distinctUsageDays }
                    .thenByDescending { it.lastUsedEpochMillis }
                    .thenBy { it.packageName },
            )

        for (candidate in candidates) {
            val appLabel = appLabels[candidate.packageName]?.trim().orEmpty()
            val averageDailyMillis = candidate.totalMillis / usageCoverageDays
            if (appLabel.isEmpty() || averageDailyMillis < MINIMUM_AVERAGE_MILLIS) continue

            val candidateIntervals = intervals.filter { it.packageName == candidate.packageName }
            val eventCoverageDays = candidateIntervals.validDays(zoneId)
            val peak = candidateIntervals.peakBucket(zoneId)
            val hasFullEvidence = eventCoverageDays >= MINIMUM_COVERAGE_DAYS && peak != null
            val proposedStart = if (hasFullEvidence) peak.startMinutes else goalDefaultStartMinutes
            if (
                enabledRoutines.any { routine ->
                    routine.fullyCovers(
                        packageName = candidate.packageName,
                        proposedStartMinutes = proposedStart,
                        proposedRepeatDays = proposedRepeatDays,
                    )
                }
            ) {
                continue
            }

            return OnboardingUsageProfileResult.Ready(
                OnboardingUsageProfile(
                    packageName = candidate.packageName,
                    appLabel = appLabel,
                    averageDailyMinutes = averageDailyMillis / 60_000,
                    suggestedStartMinutes = proposedStart,
                    usageCoverageDays = usageCoverageDays,
                    eventCoverageDays = eventCoverageDays,
                    dataQuality = if (hasFullEvidence) UsageDataQuality.Full else UsageDataQuality.UsageOnly,
                    patternType = if (hasFullEvidence) peak.patternType else UsagePatternType.TopApp,
                ),
            )
        }

        return OnboardingUsageProfileResult.Insufficient(
            usageCoverageDays = usageCoverageDays,
            eventCoverageDays = intervals.validDays(zoneId),
            reason = InsufficientReason.NoCandidate,
        )
    }

    private fun List<OnboardingUsageInterval>.validDays(zoneId: ZoneId): Int =
        asSequence().filter { it.clampedBounds(zoneId) != null }.map { it.localDate }.distinct().count()

    private fun List<OnboardingUsageInterval>.peakBucket(zoneId: ZoneId): PeakBucket? {
        val buckets = mutableMapOf<Int, MutableBucket>()
        forEach { interval ->
            val bounds = interval.clampedBounds(zoneId) ?: return@forEach
            var bucketStart = interval.localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = interval.localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            while (bucketStart < dayEnd) {
                val bucketStartZoned = Instant.ofEpochMilli(bucketStart).atZone(zoneId)
                val bucketEnd = minOf(bucketStartZoned.plusMinutes(30).toInstant().toEpochMilli(), dayEnd)
                val overlapStart = maxOf(bounds.first, bucketStart)
                val overlapEnd = minOf(bounds.second, bucketEnd)
                if (overlapEnd > overlapStart) {
                    val startMinutes = bucketStartZoned.hour * 60 + bucketStartZoned.minute
                    val bucket = buckets.getOrPut(startMinutes, ::MutableBucket)
                    bucket.totalMillis = saturatedAdd(bucket.totalMillis, overlapEnd - overlapStart)
                    bucket.days += interval.localDate
                    bucket.lastObservedEpochMillis = maxOf(bucket.lastObservedEpochMillis, overlapEnd - 1)
                }
                bucketStart = bucketEnd
            }
        }
        val evidence = buckets.entries
            .map { (startMinutes, bucket) ->
                UsageTimeBucketEvidence(
                    startMinutes = startMinutes,
                    totalMillis = bucket.totalMillis,
                    distinctDays = bucket.days.size,
                    lastObservedEpochMillis = bucket.lastObservedEpochMillis,
                )
            }
        val selectedStart = selectPeakStart(evidence) ?: return null
        val selected = evidence.first { it.startMinutes == selectedStart }
        return PeakBucket(
            startMinutes = selected.startMinutes,
            totalMillis = selected.totalMillis,
            distinctDays = selected.distinctDays,
            lastObservedEpochMillis = selected.lastObservedEpochMillis,
        )
    }

    private fun OnboardingUsageInterval.clampedBounds(zoneId: ZoneId): Pair<Long, Long>? {
        val dayStart = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val clampedStart = maxOf(startMillis, dayStart)
        val clampedEnd = minOf(endMillis, dayEnd)
        return if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
    }

    private fun EnabledRoutineCoverage.fullyCovers(
        packageName: String,
        proposedStartMinutes: Int,
        proposedRepeatDays: Set<Int>,
    ): Boolean {
        if (packageName !in packageNames || !repeatDays.containsAll(proposedRepeatDays)) return false
        val routineStart = Math.floorMod(startMinutes, MINUTES_PER_DAY)
        val routineEnd = Math.floorMod(endMinutes, MINUTES_PER_DAY)
        val proposedStart = Math.floorMod(proposedStartMinutes, MINUTES_PER_DAY)
        val routineDuration = Math.floorMod(routineEnd - routineStart, MINUTES_PER_DAY)
            .let { if (it == 0) MINUTES_PER_DAY else it }
        val proposedOffset = Math.floorMod(proposedStart - routineStart, MINUTES_PER_DAY)
        return proposedOffset + PROMISE_DURATION_MINUTES <= routineDuration
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private data class Candidate(
        val packageName: String,
        val totalMillis: Long,
        val distinctUsageDays: Int,
        val lastUsedEpochMillis: Long,
    )

    private class MutableBucket(
        var totalMillis: Long = 0,
        val days: MutableSet<java.time.LocalDate> = mutableSetOf(),
        var lastObservedEpochMillis: Long = Long.MIN_VALUE,
    )

    private data class PeakBucket(
        val startMinutes: Int,
        val totalMillis: Long,
        val distinctDays: Int,
        val lastObservedEpochMillis: Long,
    ) {
        val patternType: UsagePatternType
            get() = if (startMinutes >= 22 * 60 || startMinutes <= 5 * 60 + 30) {
                UsagePatternType.Night
            } else {
                UsagePatternType.PeakWindow
            }
    }
}
