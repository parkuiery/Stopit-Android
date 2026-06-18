package com.uiery.keep.domain.repeatblock

import com.uiery.keep.model.RoutineModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.datetime.LocalTime

private const val MIN_REPEAT_COUNT = 3
private const val DISMISS_SUPPRESSION_DAYS = 7L
private const val RAPID_RETRY_WINDOW_MINUTES = 5L
private const val MIN_RAPID_RETRY_COUNT = 2

data class RepeatBlockHistorySample(
    val startDateTime: LocalDateTime,
    val blockedPackages: List<String>,
)

data class RepeatBlockDismissedSuggestion(
    val timeBucket: RepeatBlockTimeBucket,
    val dayType: RepeatBlockDayType,
    val categoryBucket: RepeatBlockCategoryBucket,
    val dismissedAt: LocalDateTime,
)

data class RepeatBlockRoutineSuggestion(
    val timeBucket: RepeatBlockTimeBucket,
    val dayType: RepeatBlockDayType,
    val categoryBucket: RepeatBlockCategoryBucket,
    val repeatCountBucket: RepeatBlockCountBucket,
    val routineCoverageState: RoutineCoverageState,
    val reason: RepeatBlockSuggestionReason,
    val prefillPackages: List<String>,
    val prefillStartTime: LocalTime,
    val prefillEndTime: LocalTime,
)

enum class RepeatBlockTimeBucket(val analyticsValue: String) {
    Morning("morning"),
    Afternoon("afternoon"),
    Evening("evening"),
    Night("night"),
    Overnight("overnight"),
}

enum class RepeatBlockDayType(val analyticsValue: String) {
    Weekday("weekday"),
    Weekend("weekend"),
    Daily("daily"),
    CustomDays("custom_days"),
}

enum class RepeatBlockCategoryBucket(val analyticsValue: String) {
    Social("social"),
    Video("video"),
    Game("game"),
    Shopping("shopping"),
    Browser("browser"),
    Unknown("unknown"),
}

enum class RepeatBlockCountBucket(val analyticsValue: String) {
    ThreeToFive("3_5"),
    SixToTen("6_10"),
    TenPlus("10_plus"),
}

enum class RoutineCoverageState(val analyticsValue: String) {
    NotCovered("not_covered"),
    PartiallyCovered("partially_covered"),
    Covered("covered"),
}

enum class RepeatBlockSuggestionReason(val analyticsValue: String) {
    RepeatBlockTimeBucket("repeat_block_time_bucket"),
    RepeatBlockDayTime("repeat_block_day_time"),
    RapidRetry("rapid_retry"),
}

object RepeatBlockRoutineSuggestionPolicy {
    fun resolveSuggestion(
        histories: List<RepeatBlockHistorySample>,
        activeRoutines: List<RoutineModel>,
        dismissedSuggestions: List<RepeatBlockDismissedSuggestion>,
        now: LocalDateTime,
    ): RepeatBlockRoutineSuggestion? {
        val recentSamples = histories
            .filter { sample -> Duration.between(sample.startDateTime, now).toDays() in 0..13 }
            .flatMap { sample ->
                sample.blockedPackages.distinct().map { packageName ->
                    RepeatBlockSignal(
                        packageName = packageName,
                        time = sample.startDateTime,
                        timeBucket = sample.startDateTime.toRepeatBlockTimeBucket(),
                        categoryBucket = packageName.toRepeatBlockCategoryBucket(),
                    )
                }
            }

        return recentSamples
            .groupBy { signal -> signal.timeBucket to signal.categoryBucket }
            .asSequence()
            .mapNotNull { (_, signals) -> signals.toCandidateOrNull(activeRoutines, dismissedSuggestions, now) }
            .sortedWith(
                compareByDescending<RepeatBlockCandidate> { it.rapidRetryCount >= MIN_RAPID_RETRY_COUNT }
                    .thenByDescending { it.latestSeen }
                    .thenByDescending { it.repeatCount }
                    .thenBy { it.routineCoverageState.sortRank }
            )
            .firstOrNull()
            ?.toSuggestion()
    }

    private fun List<RepeatBlockSignal>.toCandidateOrNull(
        activeRoutines: List<RoutineModel>,
        dismissedSuggestions: List<RepeatBlockDismissedSuggestion>,
        now: LocalDateTime,
    ): RepeatBlockCandidate? {
        if (size < MIN_REPEAT_COUNT) return null

        val timeBucket = first().timeBucket
        val categoryBucket = first().categoryBucket
        val dayType = resolveDayType(map { it.time.dayOfWeek }.distinct())
        val packages = map { it.packageName }.distinct()
        val coverage = resolveRoutineCoverage(
            timeBucket = timeBucket,
            packages = packages,
            activeRoutines = activeRoutines,
        )
        if (coverage.state == RoutineCoverageState.Covered) return null
        if (dismissedSuggestions.isSuppressed(timeBucket, dayType, categoryBucket, now)) return null

        return RepeatBlockCandidate(
            timeBucket = timeBucket,
            dayType = dayType,
            categoryBucket = categoryBucket,
            repeatCountBucket = size.toRepeatCountBucket(),
            repeatCount = size,
            routineCoverageState = coverage.state,
            rapidRetryCount = rapidRetryCount(),
            packages = coverage.uncoveredPackages,
            latestSeen = maxOf { it.time },
        )
    }

    private fun List<RepeatBlockSignal>.rapidRetryCount(): Int =
        groupBy { signal -> signal.packageName }
            .values
            .sumOf { packageSignals ->
                packageSignals
                    .sortedBy { signal -> signal.time }
                    .zipWithNext()
                    .count { (previous, next) ->
                        Duration.between(previous.time, next.time).toMinutes() in 0..RAPID_RETRY_WINDOW_MINUTES
                    }
            }

    private fun resolveRoutineCoverage(
        timeBucket: RepeatBlockTimeBucket,
        packages: List<String>,
        activeRoutines: List<RoutineModel>,
    ): RoutineCoverage {
        val coveredPackages = activeRoutines
            .asSequence()
            .filter { routine -> routine.isEnabled && routine.covers(timeBucket) }
            .flatMap { routine -> routine.lockApplications.orEmpty().asSequence() }
            .filter { packageName -> packageName in packages }
            .toSet()
        val uncoveredPackages = packages.filterNot { packageName -> packageName in coveredPackages }

        return when {
            uncoveredPackages.isEmpty() -> RoutineCoverage(
                state = RoutineCoverageState.Covered,
                uncoveredPackages = emptyList(),
            )
            coveredPackages.isNotEmpty() -> RoutineCoverage(
                state = RoutineCoverageState.PartiallyCovered,
                uncoveredPackages = uncoveredPackages,
            )
            else -> RoutineCoverage(
                state = RoutineCoverageState.NotCovered,
                uncoveredPackages = packages,
            )
        }
    }

    private fun List<RepeatBlockDismissedSuggestion>.isSuppressed(
        timeBucket: RepeatBlockTimeBucket,
        dayType: RepeatBlockDayType,
        categoryBucket: RepeatBlockCategoryBucket,
        now: LocalDateTime,
    ): Boolean = any { dismissed ->
        dismissed.timeBucket == timeBucket &&
            dismissed.dayType == dayType &&
            dismissed.categoryBucket == categoryBucket &&
            Duration.between(dismissed.dismissedAt, now).toDays() in 0 until DISMISS_SUPPRESSION_DAYS
    }

    private fun resolveDayType(days: List<DayOfWeek>): RepeatBlockDayType {
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val daySet = days.toSet()
        return when {
            daySet.size == DayOfWeek.entries.size -> RepeatBlockDayType.Daily
            daySet.all { it in weekend } -> RepeatBlockDayType.Weekend
            daySet.none { it in weekend } -> RepeatBlockDayType.Weekday
            else -> RepeatBlockDayType.CustomDays
        }
    }

    private fun LocalDateTime.toRepeatBlockTimeBucket(): RepeatBlockTimeBucket = when (hour) {
        in 0..5 -> RepeatBlockTimeBucket.Overnight
        in 6..11 -> RepeatBlockTimeBucket.Morning
        in 12..16 -> RepeatBlockTimeBucket.Afternoon
        in 17..21 -> RepeatBlockTimeBucket.Evening
        else -> RepeatBlockTimeBucket.Night
    }

    private fun String.toRepeatBlockCategoryBucket(): RepeatBlockCategoryBucket = when {
        containsAny("instagram", "twitter", "facebook", "tiktok", "snapchat", "threads") ->
            RepeatBlockCategoryBucket.Social
        containsAny("youtube", "netflix", "video", "twitch", "disney", "wavve", "watcha") ->
            RepeatBlockCategoryBucket.Video
        containsAny("game", "games", "roblox", "minecraft", "supercell") ->
            RepeatBlockCategoryBucket.Game
        containsAny("shop", "store", "commerce", "amazon", "coupang", "gmarket") ->
            RepeatBlockCategoryBucket.Shopping
        containsAny("browser", "chrome", "safari", "firefox", "whale") ->
            RepeatBlockCategoryBucket.Browser
        else -> RepeatBlockCategoryBucket.Unknown
    }

    private fun String.containsAny(vararg needles: String): Boolean {
        val lower = lowercase()
        return needles.any { needle -> needle in lower }
    }

    private fun Int.toRepeatCountBucket(): RepeatBlockCountBucket = when {
        this <= 5 -> RepeatBlockCountBucket.ThreeToFive
        this <= 10 -> RepeatBlockCountBucket.SixToTen
        else -> RepeatBlockCountBucket.TenPlus
    }

    private fun RoutineModel.covers(timeBucket: RepeatBlockTimeBucket): Boolean {
        val (bucketStart, bucketEnd) = timeBucket.prefillWindow()
        return timeWindowContains(bucketStart) && timeWindowContains(bucketEnd.minusOneMinuteForCoverage())
    }

    private fun RoutineModel.timeWindowContains(time: LocalTime): Boolean {
        if (startTime == endTime) return true
        return if (startTime < endTime) {
            time >= startTime && time < endTime
        } else {
            time >= startTime || time < endTime
        }
    }

    private fun RepeatBlockTimeBucket.prefillWindow(): Pair<LocalTime, LocalTime> = when (this) {
        RepeatBlockTimeBucket.Morning -> LocalTime(6, 0) to LocalTime(12, 0)
        RepeatBlockTimeBucket.Afternoon -> LocalTime(12, 0) to LocalTime(17, 0)
        RepeatBlockTimeBucket.Evening -> LocalTime(17, 0) to LocalTime(22, 0)
        RepeatBlockTimeBucket.Night -> LocalTime(22, 0) to LocalTime(0, 0)
        RepeatBlockTimeBucket.Overnight -> LocalTime(0, 0) to LocalTime(6, 0)
    }

    private fun LocalTime.minusOneMinuteForCoverage(): LocalTime = when {
        hour == 0 && minute == 0 -> LocalTime(23, 59)
        minute == 0 -> LocalTime(hour - 1, 59)
        else -> LocalTime(hour, minute - 1)
    }

    private data class RepeatBlockSignal(
        val packageName: String,
        val time: LocalDateTime,
        val timeBucket: RepeatBlockTimeBucket,
        val categoryBucket: RepeatBlockCategoryBucket,
    )

    private data class RoutineCoverage(
        val state: RoutineCoverageState,
        val uncoveredPackages: List<String>,
    )

    private data class RepeatBlockCandidate(
        val timeBucket: RepeatBlockTimeBucket,
        val dayType: RepeatBlockDayType,
        val categoryBucket: RepeatBlockCategoryBucket,
        val repeatCountBucket: RepeatBlockCountBucket,
        val repeatCount: Int,
        val routineCoverageState: RoutineCoverageState,
        val rapidRetryCount: Int,
        val packages: List<String>,
        val latestSeen: LocalDateTime,
    ) {
        fun toSuggestion(): RepeatBlockRoutineSuggestion {
            val (start, end) = timeBucket.prefillWindow()
            return RepeatBlockRoutineSuggestion(
                timeBucket = timeBucket,
                dayType = dayType,
                categoryBucket = categoryBucket,
                repeatCountBucket = repeatCountBucket,
                routineCoverageState = routineCoverageState,
                reason = if (rapidRetryCount >= MIN_RAPID_RETRY_COUNT) {
                    RepeatBlockSuggestionReason.RapidRetry
                } else {
                    RepeatBlockSuggestionReason.RepeatBlockTimeBucket
                },
                prefillPackages = packages,
                prefillStartTime = start,
                prefillEndTime = end,
            )
        }
    }

    private val RoutineCoverageState.sortRank: Int
        get() = when (this) {
            RoutineCoverageState.NotCovered -> 0
            RoutineCoverageState.PartiallyCovered -> 1
            RoutineCoverageState.Covered -> 2
        }
}
