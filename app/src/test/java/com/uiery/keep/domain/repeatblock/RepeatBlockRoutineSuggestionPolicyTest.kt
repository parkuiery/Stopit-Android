package com.uiery.keep.domain.repeatblock

import com.uiery.keep.model.RoutineModel
import java.time.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatBlockRoutineSuggestionPolicyTest {
    @Test
    fun repeatedNightSocialBlocksCreateOneEditableRoutineSuggestion() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.twitter.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = now,
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockDayType.Weekday, suggestion.dayType)
        assertEquals(RepeatBlockCategoryBucket.Social, suggestion.categoryBucket)
        assertEquals(RepeatBlockCountBucket.ThreeToFive, suggestion.repeatCountBucket)
        assertEquals(RoutineCoverageState.NotCovered, suggestion.routineCoverageState)
        assertEquals(RepeatBlockSuggestionReason.RepeatBlockTimeBucket, suggestion.reason)
        assertEquals(listOf("com.instagram.android", "com.twitter.android"), suggestion.prefillPackages)
        assertEquals(22, suggestion.prefillStartTime.hour)
        assertEquals(0, suggestion.prefillStartTime.minute)
        assertEquals(0, suggestion.prefillEndTime.hour)
        assertEquals(0, suggestion.prefillEndTime.minute)
    }

    @Test
    fun unclassifiedAppsDoNotBecomeOneCrowdJustBecauseTheTimeMatches() {
        // 부류를 모르는 앱을 시간대만으로 묶으면, 저녁에 잠근 앱 수십 개가 통째로 한 제안이
        // 되어 사용자는 자기가 만들 리 없는 루틴을 권유받는다. Unknown 은 "같은 부류"가
        // 아니라 "모른다"는 뜻이다.
        val histories = listOf(
            history("2026-06-05T19:20:00", "com.example.alpha"),
            history("2026-06-04T19:05:00", "com.example.beta"),
            history("2026-06-03T19:45:00", "com.example.gamma"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
        )

        assertNull(suggestion)
    }

    @Test
    fun oneUnclassifiedAppRepeatedInTheSameHoursStillEarnsASuggestion() {
        // 따로 세는 대신 같은 앱을 그 시간대에 세 번 이상 잠갔다면 그것이 원래 말하려던
        // "반복"이다.
        val histories = listOf(
            history("2026-06-05T19:20:00", "com.example.alpha"),
            history("2026-06-04T19:05:00", "com.example.alpha"),
            history("2026-06-03T19:45:00", "com.example.alpha"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Evening, suggestion.timeBucket)
        assertEquals(RepeatBlockCategoryBucket.Unknown, suggestion.categoryBucket)
        assertEquals(listOf("com.example.alpha"), suggestion.prefillPackages)
    }

    @Test
    fun aResolverThatKnowsTheAppOutranksTheNameGuess() {
        // 이름에 영문 키워드가 없어 이름만으로는 Unknown 인 앱들이다. 시스템이 부류를 알려
        // 주면 서로 다른 앱이어도 하나의 습관으로 묶여 제안이 된다.
        val histories = listOf(
            history("2026-06-05T19:20:00", "com.example.alpha"),
            history("2026-06-04T19:05:00", "com.example.beta"),
            history("2026-06-03T19:45:00", "com.example.gamma"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
            categoryOf = { RepeatBlockCategoryBucket.Video },
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockCategoryBucket.Video, suggestion.categoryBucket)
        assertEquals(
            listOf("com.example.alpha", "com.example.beta", "com.example.gamma"),
            suggestion.prefillPackages,
        )
    }

    @Test
    fun theNameGuessPrefersBeingRightOverBeingBroad() {
        assertEquals(
            RepeatBlockCategoryBucket.Social,
            repeatBlockCategoryFromPackageName("com.kakao.talk"),
        )
        // 영상 판정이 쇼핑보다 앞이라 coupangplay 는 쿠팡이 아니라 영상으로 읽힌다.
        assertEquals(
            RepeatBlockCategoryBucket.Video,
            repeatBlockCategoryFromPackageName("com.coupang.coupangplay"),
        )
        assertEquals(
            RepeatBlockCategoryBucket.Shopping,
            repeatBlockCategoryFromPackageName("com.coupang.mobile"),
        )
        // 짐작할 근거가 없으면 억지로 맞히지 않는다.
        assertEquals(
            RepeatBlockCategoryBucket.Unknown,
            repeatBlockCategoryFromPackageName("com.nhn.android.search"),
        )
    }

    @Test
    fun activeRoutineCoveringSameAppsAndTimeSuppressesSuggestion() {
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.instagram.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )
        val routine = routine(
            repeatDays = "1111111",
            startTime = LocalTime(22, 0),
            endTime = LocalTime(0, 0),
            apps = listOf("com.instagram.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = listOf(routine),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
        )

        assertNull(suggestion)
    }

    @Test
    fun partiallyCoveredRoutinePrefillsOnlyUncoveredApps() {
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-05T23:25:00", "com.twitter.android"),
            history("2026-06-04T23:05:00", "com.instagram.android"),
            history("2026-06-04T23:10:00", "com.twitter.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
            history("2026-06-03T22:50:00", "com.twitter.android"),
        )
        val routine = routine(
            repeatDays = "1111111",
            startTime = LocalTime(22, 0),
            endTime = LocalTime(0, 0),
            apps = listOf("com.instagram.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = listOf(routine),
            dismissedSuggestions = emptyList(),
            now = LocalDateTime.of(2026, 6, 6, 12, 0),
        )

        requireNotNull(suggestion)
        assertEquals(RoutineCoverageState.PartiallyCovered, suggestion.routineCoverageState)
        assertEquals(listOf("com.twitter.android"), suggestion.prefillPackages)
    }

    @Test
    fun dismissedSuggestionIsSuppressedForSevenDays() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.instagram.android"),
            history("2026-06-04T23:05:00", "com.twitter.android"),
            history("2026-06-03T22:45:00", "com.instagram.android"),
        )
        val dismissed = RepeatBlockDismissedSuggestion(
            timeBucket = RepeatBlockTimeBucket.Night,
            dayType = RepeatBlockDayType.Weekday,
            categoryBucket = RepeatBlockCategoryBucket.Social,
            dismissedAt = LocalDateTime.of(2026, 6, 1, 9, 0),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = listOf(dismissed),
            now = now,
        )

        assertNull(suggestion)
    }

    @Test
    fun onlyMostRecentHighestValueCandidateIsReturned() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:20:00", "com.youtube.android"),
            history("2026-06-04T23:05:00", "com.youtube.android"),
            history("2026-06-03T22:45:00", "com.netflix.mediaclient"),
            history("2026-06-02T19:10:00", "com.instagram.android"),
            history("2026-06-01T19:05:00", "com.instagram.android"),
            history("2026-05-31T19:00:00", "com.twitter.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = now,
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockCategoryBucket.Video, suggestion.categoryBucket)
        assertEquals(listOf("com.youtube.android", "com.netflix.mediaclient"), suggestion.prefillPackages)
    }

    @Test
    fun rapidRetryCandidateUsesRapidRetryReasonAndOutranksNewerNormalCandidate() {
        val now = LocalDateTime.of(2026, 6, 6, 12, 0)
        val histories = listOf(
            history("2026-06-05T23:10:00", "com.instagram.android"),
            history("2026-06-05T23:13:00", "com.instagram.android"),
            history("2026-06-05T23:17:00", "com.instagram.android"),
            history("2026-06-06T19:30:00", "com.youtube.android"),
            history("2026-06-05T19:30:00", "com.youtube.android"),
            history("2026-06-04T19:30:00", "com.youtube.android"),
        )

        val suggestion = RepeatBlockRoutineSuggestionPolicy.resolveSuggestion(
            histories = histories,
            activeRoutines = emptyList(),
            dismissedSuggestions = emptyList(),
            now = now,
        )

        requireNotNull(suggestion)
        assertEquals(RepeatBlockTimeBucket.Night, suggestion.timeBucket)
        assertEquals(RepeatBlockCategoryBucket.Social, suggestion.categoryBucket)
        assertEquals(RepeatBlockSuggestionReason.RapidRetry, suggestion.reason)
        assertEquals(listOf("com.instagram.android"), suggestion.prefillPackages)
    }

    private fun history(start: String, packageName: String): RepeatBlockHistorySample =
        RepeatBlockHistorySample(
            startDateTime = LocalDateTime.parse(start),
            blockedPackages = listOf(packageName),
        )

    private fun routine(
        repeatDays: String,
        startTime: LocalTime,
        endTime: LocalTime,
        apps: List<String>,
    ): RoutineModel = RoutineModel(
        id = 1,
        name = "Night routine",
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays,
        lockApplications = apps,
        isEnabled = true,
    )
}
