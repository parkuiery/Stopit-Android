package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.RecordingKeepAnalytics
import com.uiery.keep.receiver.RoutineReceiverPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelRoutineStartNoticeTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-11T14:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun maybeDrainRoutineStartNoticeClearsPendingMessageAndUpdatesSnackbarMessage() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE to "Morning focus started without notification permission",
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore)

        delay(50)
        viewModel.maybeDrainRoutineStartNotice()
        delay(50)

        assertEquals(
            "Morning focus started without notification permission",
            viewModel.container.stateFlow.value.snackbarMessage,
        )
        assertEquals(null, dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE])
    }

    @Test
    fun maybeDrainRoutineStartNoticeDrainsOnlyFirstQueuedMessageAndKeepsRemainder() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE to
                    RoutineReceiverPolicy.encodePendingRoutineStartNotices(
                        listOf(
                            "Morning focus started without notification permission",
                            "Evening focus started without notification permission",
                        ),
                    )!!,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore)

        delay(50)
        viewModel.maybeDrainRoutineStartNotice()
        delay(50)

        assertEquals(
            "Morning focus started without notification permission",
            viewModel.container.stateFlow.value.snackbarMessage,
        )
        assertEquals(
            listOf("Evening focus started without notification permission"),
            RoutineReceiverPolicy.decodePendingRoutineStartNotices(
                dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
            ),
        )
    }

    @Test
    fun maybeDrainRoutineStartNoticeKeepsPendingMessageWhenSheetVisible() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE to "Morning focus started without notification permission",
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore)

        delay(50)
        viewModel.showCategoryBottomSheet()
        delay(50)
        viewModel.maybeDrainRoutineStartNotice()
        delay(50)

        assertEquals("", viewModel.container.stateFlow.value.snackbarMessage)
        assertEquals(
            "Morning focus started without notification permission",
            dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
        )
    }

    @Test
    fun hideCategoryBottomSheetDrainsPendingRoutineStartNoticeAfterSheetDismisses() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE to "Morning focus started without notification permission",
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore)

        delay(50)
        viewModel.showCategoryBottomSheet()
        delay(50)
        viewModel.maybeDrainRoutineStartNotice()
        delay(50)
        viewModel.hideCategoryBottomSheet()
        delay(50)

        assertEquals(
            "Morning focus started without notification permission",
            viewModel.container.stateFlow.value.snackbarMessage,
        )
        assertEquals(null, dataStore.snapshot()[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE])
    }

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: KeepAnalytics = RecordingKeepAnalytics(),
        lockHistoryDao: LockHistoryDao = FakeLockHistoryDao(),
    ): HomeViewModel =
        HomeViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = lockHistoryDao,
            reviewEligibility = ReviewEligibilityEvaluator(
                dataStore = dataStore,
                remoteConfig = FakeReviewRemoteConfig(enabled = true),
                accessibilityChecker = FakeAccessibilityChecker(enabled = true),
                emergencyUnlockDao = FakeEmergencyUnlockDao(),
                lockHistoryDao = FakeLockHistoryDao(recentSuccessCount = 2),
                clock = clock,
                buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
            ),
            inAppReviewManager = InAppReviewManager(
                launcher = FakeReviewLauncher(),
                analytics = analytics,
                dataStore = dataStore,
                clock = clock,
            ),
        )
}
