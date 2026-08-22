package com.uiery.keep.feature.review

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 두 종료 경로(타이머 완주 / 수동 토글 오프)가 같은 arm 계약을 쓰는지 고정한다.
 *
 * 배경: 2026-08 이전에는 arm 평가가 타이머 완주 경로에만 있어 종료 세션의 96%가
 * 평가조차 받지 못했다. docs/REVIEW_PROMPT_LIFECYCLE.md 의 "수동 종료 arm 경로" 절 참조.
 */
class ReviewPromptArmerTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val nowInstant: Instant = Instant.parse("2026-05-11T14:30:00Z")
    private val nowMs: Long = nowInstant.toEpochMilli()
    private val clock: Clock = Clock.fixed(nowInstant, zone)

    private fun prefs(successfulSessionCount: Int = 5) = mutablePreferencesOf().apply {
        set(PreferencesKey.SUCCESSFUL_SESSION_COUNT, successfulSessionCount)
        set(PreferencesKey.LAST_BACKGROUNDED_AT_MS, nowMs - 5_000)
    }

    private class Fixture(
        val armer: ReviewPromptArmer,
        val analytics: RecordingKeepAnalytics,
        val blockingStateStore: BlockingStateStore,
        val reviewPromptStateStore: ReviewPromptStateStore,
    )

    private fun newFixture(successfulSessionCount: Int = 5): Fixture {
        val dataStore = FakeDataStore(prefs(successfulSessionCount))
        val blockingStateStore = BlockingStateStore(dataStore)
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        val analytics = RecordingKeepAnalytics()
        val evaluator = ReviewEligibilityEvaluator(
            blockingStateStore = blockingStateStore,
            reviewPromptStateStore = reviewPromptStateStore,
            remoteConfig = FakeReviewRemoteConfig(true),
            accessibilityChecker = FakeAccessibilityChecker(true),
            repository = fakeReviewEligibilityRepository(emergencyCount = 0, recentSuccessCount = 1),
            clock = clock,
            buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
        )
        return Fixture(
            armer = ReviewPromptArmer(
                blockingStateStore = blockingStateStore,
                reviewPromptStateStore = reviewPromptStateStore,
                reviewEligibility = evaluator,
                analytics = analytics,
                clock = clock,
            ),
            analytics = analytics,
            blockingStateStore = blockingStateStore,
            reviewPromptStateStore = reviewPromptStateStore,
        )
    }

    @Test
    fun timerElapsedPathArmsWithoutDurationFloor() = runBlocking {
        val fixture = newFixture()

        // 완주 자체가 의도된 성공이므로 1분짜리 타이머도 하한에 걸리지 않는다.
        fixture.armer.arm(
            sessionStartMillis = nowMs - 60_000L,
            isRoutine = false,
        )

        assertEquals(listOf(AnalyticsEventRecord.Eligible), fixture.analytics.events)
        assertTrue(fixture.reviewPromptStateStore.readState().isPending)
        assertEquals(6, fixture.blockingStateStore.readSuccessfulSessionCount())
    }

    @Test
    fun manualStopLongerThanFloorArms() = runBlocking {
        val fixture = newFixture()

        fixture.armer.arm(
            sessionStartMillis = nowMs - (MANUAL_SESSION_MIN_MILLIS + 1),
            isRoutine = false,
            minimumDurationMillis = MANUAL_SESSION_MIN_MILLIS,
        )

        assertEquals(listOf(AnalyticsEventRecord.Eligible), fixture.analytics.events)
        assertTrue(fixture.reviewPromptStateStore.readState().isPending)
        assertEquals(6, fixture.blockingStateStore.readSuccessfulSessionCount())
    }

    @Test
    fun manualStopShorterThanFloorIsSkippedAndDoesNotCount() = runBlocking {
        val fixture = newFixture()

        fixture.armer.arm(
            sessionStartMillis = nowMs - (MANUAL_SESSION_MIN_MILLIS - 1),
            isRoutine = false,
            minimumDurationMillis = MANUAL_SESSION_MIN_MILLIS,
        )

        assertEquals(
            listOf(AnalyticsEventRecord.Skipped(SkipReason.BelowManualSessionDuration.name)),
            fixture.analytics.events,
        )
        assertFalse(fixture.reviewPromptStateStore.readState().isPending)
        // 핵심 계약: 짧은 오조작은 성공 세션 카운터를 밀어 올리면 안 된다.
        assertEquals(5, fixture.blockingStateStore.readSuccessfulSessionCount())
    }

    @Test
    fun ineligibleDecisionIsReportedWithItsOwnReason() = runBlocking {
        // 성공 세션이 0인 상태에서 arm 하면 카운터가 1이 되어 임계값을 만족한다.
        // 대신 quiet hours 로 막히는 경로를 확인한다.
        val dataStore = FakeDataStore(prefs(successfulSessionCount = 5))
        val blockingStateStore = BlockingStateStore(dataStore)
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        val analytics = RecordingKeepAnalytics()
        val quietInstant = Instant.parse("2026-05-11T18:30:00Z") // KST 03:30 -> quiet hours
        val quietClock = Clock.fixed(quietInstant, zone)
        val armer = ReviewPromptArmer(
            blockingStateStore = blockingStateStore,
            reviewPromptStateStore = reviewPromptStateStore,
            reviewEligibility = ReviewEligibilityEvaluator(
                blockingStateStore = blockingStateStore,
                reviewPromptStateStore = reviewPromptStateStore,
                remoteConfig = FakeReviewRemoteConfig(true),
                accessibilityChecker = FakeAccessibilityChecker(true),
                repository = fakeReviewEligibilityRepository(emergencyCount = 0, recentSuccessCount = 1),
                clock = quietClock,
                buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
            ),
            analytics = analytics,
            clock = quietClock,
        )

        armer.arm(
            sessionStartMillis = quietInstant.toEpochMilli() - 60_000L,
            isRoutine = false,
        )

        assertEquals(
            listOf(AnalyticsEventRecord.Skipped(SkipReason.QuietHours.name)),
            analytics.events,
        )
        assertFalse(reviewPromptStateStore.readState().isPending)
    }
}
