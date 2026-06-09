package com.uiery.keep.feature.lock.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CountDownContentTest {
    @Test
    fun countdownDayPrefixCountDoesNotShowDayPrefixBelowTwentyFourHours() {
        assertEquals(0, countdownDayPrefixCount(totalSeconds = 86_399))
    }

    @Test
    fun countdownDayPrefixCountShowsOneDayAtTwentyFourHours() {
        assertEquals(1, countdownDayPrefixCount(totalSeconds = 86_400))
    }

    @Test
    fun countdownDayPrefixCountShowsTwoDaysAtFortyEightHours() {
        assertEquals(2, countdownDayPrefixCount(totalSeconds = 172_800))
    }

    @Test
    fun countdownDisplaySecondsClampsExpiredCountdownToZero() {
        assertEquals(0, countdownDisplaySeconds(totalSeconds = -1))
        assertEquals(0, countdownDisplaySeconds(totalSeconds = 0))
        assertEquals(1, countdownDisplaySeconds(totalSeconds = 1))
    }

    @Test
    fun countdownRemainingSecondsWithinDayNeverReturnsNegativeRemainder() {
        assertEquals(0, countdownRemainingSecondsWithinDay(totalSeconds = -1))
        assertEquals(0, countdownRemainingSecondsWithinDay(totalSeconds = 0))
        assertEquals(1, countdownRemainingSecondsWithinDay(totalSeconds = 1))
        assertEquals(0, countdownRemainingSecondsWithinDay(totalSeconds = 86_400))
    }
}
