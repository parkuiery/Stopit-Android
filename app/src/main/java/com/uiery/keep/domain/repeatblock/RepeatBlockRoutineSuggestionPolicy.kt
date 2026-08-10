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

/**
 * 이름만 보고 부류를 짐작하는 마지막 수단.
 *
 * 시스템이 밝힌 카테고리가 없을 때만 쓴다. 영문 키워드를 담지 않은 이름은 알아볼 수 없으므로
 * 여기서 못 맞히는 앱이 있는 것은 정상이고, 그런 앱은 Unknown 으로 남아 패키지별로 따로
 * 세어진다. 그래서 이 목록은 넓히기보다 **틀리지 않는 것**이 중요하다. 어림짐작으로 잘못
 * 분류하면 서로 무관한 앱이 한 제안으로 묶여 나간다.
 *
 * 겹치는 이름이 있을 수 있어 순서가 곧 우선순위다. 예를 들어 `coupangplay` 는 쇼핑이 아니라
 * 영상이므로 영상 판정이 앞에 온다.
 */
fun repeatBlockCategoryFromPackageName(packageName: String): RepeatBlockCategoryBucket {
    val name = packageName.lowercase()
    fun matches(vararg needles: String) = needles.any { needle -> needle in name }

    return when {
        matches(
            "instagram", "twitter", "facebook", "tiktok", "snapchat", "threads",
            "kakao.talk", "discord", "reddit", "pinterest", "android.band",
        ) -> RepeatBlockCategoryBucket.Social

        matches(
            "youtube", "netflix", "video", "twitch", "disney", "wavve", "watcha",
            "tving", "chzzk", "afreeca", "sooplive", "laftel", "coupangplay",
        ) -> RepeatBlockCategoryBucket.Video

        matches(
            "game", "games", "roblox", "minecraft", "supercell",
            "netmarble", "nexon", "ncsoft", "krafton", "com2us",
        ) -> RepeatBlockCategoryBucket.Game

        matches(
            "shop", "store", "commerce", "amazon", "coupang", "gmarket",
            "11st", "musinsa", "aliexpress", "temu", "danawa",
        ) -> RepeatBlockCategoryBucket.Shopping

        matches("browser", "chrome", "safari", "firefox", "whale", "duckduckgo") ->
            RepeatBlockCategoryBucket.Browser

        else -> RepeatBlockCategoryBucket.Unknown
    }
}

object RepeatBlockRoutineSuggestionPolicy {
    /**
     * [categoryOf] 는 앱이 어떤 부류인지 답한다. 기본값은 이름으로 짐작하는 마지막 수단이고,
     * 설치된 앱의 시스템 카테고리를 읽을 수 있는 호출자는 더 정확한 판단을 넘긴다.
     */
    fun resolveSuggestion(
        histories: List<RepeatBlockHistorySample>,
        activeRoutines: List<RoutineModel>,
        dismissedSuggestions: List<RepeatBlockDismissedSuggestion>,
        now: LocalDateTime,
        categoryOf: (String) -> RepeatBlockCategoryBucket = ::repeatBlockCategoryFromPackageName,
    ): RepeatBlockRoutineSuggestion? {
        val recentSamples = histories
            .filter { sample -> Duration.between(sample.startDateTime, now).toDays() in 0..13 }
            .flatMap { sample ->
                sample.blockedPackages.distinct().map { packageName ->
                    RepeatBlockSignal(
                        packageName = packageName,
                        time = sample.startDateTime,
                        timeBucket = sample.startDateTime.toRepeatBlockTimeBucket(),
                        categoryBucket = categoryOf(packageName),
                    )
                }
            }

        return recentSamples
            .groupBy(RepeatBlockSignal::groupKey)
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
    ) {
        /**
         * 무엇을 "같은 반복"으로 볼지.
         *
         * 부류를 아는 앱끼리는 부류로 묶는다. 저녁마다 다른 영상 앱을 열었다면 그건 하나의
         * 습관이고, 제안도 하나여야 한다.
         *
         * 부류를 모르는 앱은 패키지로 따로 센다. Unknown 은 "같은 부류"가 아니라 "모른다"는
         * 뜻이라, 시간대가 같다는 이유로 묶으면 서로 아무 관계없는 앱 수십 개가 한 제안이
         * 되어 사용자는 자기가 만들 리 없는 루틴을 권유받는다. 따로 세면 같은 앱을 그
         * 시간대에 세 번 이상 잠근 경우만 남아, 원래 의도한 "반복"이 된다.
         */
        val groupKey: Pair<RepeatBlockTimeBucket, String>
            get() = timeBucket to when (categoryBucket) {
                RepeatBlockCategoryBucket.Unknown -> "package:$packageName"
                else -> categoryBucket.analyticsValue
            }
    }

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
