package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.AnalyticsEventRecord
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.RecordingKeepAnalytics
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelReviewTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-11T14:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun maybeDrainReviewFlagClearsPendingWhenLiveEligibilityFails() = runBlocking {
        val analytics = RecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.REVIEW_PENDING to true,
            ),
        )
        val reviewEligibility = ReviewEligibilityEvaluator(
            dataStore = dataStore,
            remoteConfig = FakeReviewRemoteConfig(enabled = true),
            accessibilityChecker = FakeAccessibilityChecker(enabled = false),
            emergencyUnlockDao = FakeEmergencyUnlockDao(),
            lockHistoryDao = FakeLockHistoryDao(),
            clock = clock,
            buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
        )
        val viewModel = HomeViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = FakeLockHistoryDao(),
            reviewEligibility = reviewEligibility,
            inAppReviewManager = InAppReviewManager(
                launcher = FakeReviewLauncher(),
                analytics = analytics,
                dataStore = dataStore,
                clock = clock,
            ),
        )

        viewModel.maybeDrainReviewFlag(activity = null)
        delay(50)

        assertEquals(listOf(AnalyticsEventRecord.Skipped("AccessibilityOff")), analytics.events)
        assertEquals(false, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }
}
