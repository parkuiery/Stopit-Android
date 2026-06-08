package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.goallock.GoalLockRepository
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.RecordingKeepAnalytics
import com.uiery.keep.feature.review.fakeReviewEligibilityRepository
import com.uiery.keep.receiver.RoutineReceiverPolicy
import com.uiery.keep.service.LockHistoryRecorder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    ): HomeViewModel {
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        return HomeViewModel(
            dataStore = dataStore,
            blockingStateStore = BlockingStateStore(dataStore),
            reviewPromptStateStore = reviewPromptStateStore,
            routineNoticeStore = RoutineNoticeStore(dataStore),
            analytics = analytics,
            lockHistoryRecorder = LockHistoryRecorder(dataStore, LockHistorySessionWriter(lockHistoryDao)),
            goalLockRepository = GoalLockRepository(EmptyRoutineNoticeGoalLockDao()),
            reviewEligibility = ReviewEligibilityEvaluator(
                blockingStateStore = BlockingStateStore(dataStore),
                reviewPromptStateStore = reviewPromptStateStore,
                remoteConfig = FakeReviewRemoteConfig(enabled = true),
                accessibilityChecker = FakeAccessibilityChecker(enabled = true),
                repository = fakeReviewEligibilityRepository(recentSuccessCount = 2),
                clock = clock,
                buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
            ),
            inAppReviewManager = InAppReviewManager(
                launcher = FakeReviewLauncher(),
                analytics = analytics,
                reviewPromptStateStore = reviewPromptStateStore,
                clock = clock,
            ),
        )
    }
}

private class EmptyRoutineNoticeGoalLockDao : GoalLockDao {
    override fun fetchAll(): Flow<List<GoalLockEntity>> = flowOf(emptyList())

    override fun fetch(id: Long): GoalLockEntity? = null

    override fun insert(goalLock: GoalLockEntity): Long = goalLock.id

    override fun update(goalLock: GoalLockEntity) = Unit
}
