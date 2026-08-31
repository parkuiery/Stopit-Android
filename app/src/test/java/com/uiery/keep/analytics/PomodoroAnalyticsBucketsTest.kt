package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * bucket 경계가 조용히 바뀌면 배포 전후 수치를 같은 분자/분모로 비교할 수 없게 된다.
 * `docs/POMODORO_FOCUS_MVP.md` 의 "권장 enum/bucket" 을 그대로 고정한다.
 */
class PomodoroAnalyticsBucketsTest {

    /**
     * 커스텀 길이는 `preset` enum 으로 알 수 없으므로 이 bucket 이 유일한 길이 신호다.
     * 두 프리셋이 서로 다른 칸에 떨어져야 프리셋과 커스텀 분포를 같은 축에서 비교할 수 있다.
     */
    @Test
    fun focusMinutesBucketSeparatesTheTwoPresets() {
        assertEquals("25_34", PomodoroAnalyticsBuckets.focusMinutesBucket(25))
        assertEquals("50_plus", PomodoroAnalyticsBuckets.focusMinutesBucket(50))
    }

    @Test
    fun focusMinutesBucketFollowsTheDocumentedBoundaries() {
        assertEquals("0_14", PomodoroAnalyticsBuckets.focusMinutesBucket(5))
        assertEquals("0_14", PomodoroAnalyticsBuckets.focusMinutesBucket(14))
        assertEquals("15_24", PomodoroAnalyticsBuckets.focusMinutesBucket(15))
        assertEquals("15_24", PomodoroAnalyticsBuckets.focusMinutesBucket(24))
        assertEquals("25_34", PomodoroAnalyticsBuckets.focusMinutesBucket(34))
        assertEquals("35_49", PomodoroAnalyticsBuckets.focusMinutesBucket(35))
        assertEquals("35_49", PomodoroAnalyticsBuckets.focusMinutesBucket(49))
        assertEquals("50_plus", PomodoroAnalyticsBuckets.focusMinutesBucket(120))
    }

    @Test
    fun cycleIndexBucketFollowsTheDocumentedBoundaries() {
        assertEquals("1", PomodoroAnalyticsBuckets.cycleIndexBucket(1))
        assertEquals("2_3", PomodoroAnalyticsBuckets.cycleIndexBucket(2))
        assertEquals("2_3", PomodoroAnalyticsBuckets.cycleIndexBucket(3))
        assertEquals("4_6", PomodoroAnalyticsBuckets.cycleIndexBucket(4))
        assertEquals("4_6", PomodoroAnalyticsBuckets.cycleIndexBucket(6))
        assertEquals("7_plus", PomodoroAnalyticsBuckets.cycleIndexBucket(7))
    }

    @Test
    fun cycleIndexBucketClampsNonsenseInputIntoTheLowestBucket() {
        assertEquals("1", PomodoroAnalyticsBuckets.cycleIndexBucket(0))
        assertEquals("1", PomodoroAnalyticsBuckets.cycleIndexBucket(-3))
    }

    @Test
    fun completedFocusCountBucketSeparatesZeroFromOne() {
        // 한 번도 못 끝낸 세션과 한 번 끝낸 세션은 다른 이야기다.
        assertEquals("0", PomodoroAnalyticsBuckets.completedFocusCountBucket(0))
        assertEquals("1", PomodoroAnalyticsBuckets.completedFocusCountBucket(1))
        assertEquals("2_3", PomodoroAnalyticsBuckets.completedFocusCountBucket(3))
        assertEquals("4_6", PomodoroAnalyticsBuckets.completedFocusCountBucket(4))
        assertEquals("7_plus", PomodoroAnalyticsBuckets.completedFocusCountBucket(9))
    }

    @Test
    fun elapsedMinutesBucketFollowsTheDocumentedBoundaries() {
        assertEquals("0_9", PomodoroAnalyticsBuckets.elapsedMinutesBucket(0))
        assertEquals("0_9", PomodoroAnalyticsBuckets.elapsedMinutesBucket(9))
        assertEquals("10_29", PomodoroAnalyticsBuckets.elapsedMinutesBucket(10))
        assertEquals("10_29", PomodoroAnalyticsBuckets.elapsedMinutesBucket(29))
        assertEquals("30_59", PomodoroAnalyticsBuckets.elapsedMinutesBucket(30))
        assertEquals("60_119", PomodoroAnalyticsBuckets.elapsedMinutesBucket(60))
        assertEquals("120_plus", PomodoroAnalyticsBuckets.elapsedMinutesBucket(120))
        assertEquals("120_plus", PomodoroAnalyticsBuckets.elapsedMinutesBucket(10_000))
    }

    @Test
    fun selectedAppCountBucketMatchesTheExistingAppCountVocabulary() {
        assertEquals("1", PomodoroAnalyticsBuckets.selectedAppCountBucket(1))
        assertEquals("2_3", PomodoroAnalyticsBuckets.selectedAppCountBucket(2))
        assertEquals("4_6", PomodoroAnalyticsBuckets.selectedAppCountBucket(5))
        assertEquals("7_plus", PomodoroAnalyticsBuckets.selectedAppCountBucket(12))
    }
}
