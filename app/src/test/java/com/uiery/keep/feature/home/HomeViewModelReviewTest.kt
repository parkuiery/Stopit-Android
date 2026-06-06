package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.goallock.GoalLockRepository
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.feature.review.AnalyticsEventRecord
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.RecordingKeepAnalytics
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.ReviewLaunchResult
import com.uiery.keep.feature.review.fakeReviewEligibilityRepository
import com.uiery.keep.model.RoutineModel
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
import org.mockito.Mockito

class HomeViewModelReviewTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-11T14:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun maybeDrainReviewFlagKeepsPendingWhenHomeSheetIsVisibleAndReevaluatesAfterDismiss() = runBlocking {
        val analytics = RecordingKeepAnalytics()
        val dataStore = pendingReviewDataStore()
        val accessibilityChecker = FakeAccessibilityChecker(enabled = true)
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            accessibilityChecker = accessibilityChecker,
        )

        viewModel.showCategoryBottomSheet()
        delay(50)
        viewModel.maybeDrainReviewFlag(activity = null)
        delay(50)

        assertEquals(listOf(AnalyticsEventRecord.Skipped("NotHomeRoot")), analytics.events)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])

        accessibilityChecker.enabled = false
        viewModel.hideCategoryBottomSheet()
        delay(50)
        viewModel.maybeDrainReviewFlag(activity = null)
        delay(50)

        assertEquals(
            listOf(
                AnalyticsEventRecord.Skipped("NotHomeRoot"),
                AnalyticsEventRecord.Skipped("AccessibilityOff"),
            ),
            analytics.events,
        )
        assertEquals(false, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }

    @Test
    fun maybeDrainReviewFlagClearsPendingWhenLiveEligibilityFails() = runBlocking {
        val analytics = RecordingKeepAnalytics()
        val dataStore = pendingReviewDataStore()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            accessibilityChecker = FakeAccessibilityChecker(enabled = false),
        )

        viewModel.maybeDrainReviewFlag(activity = null)
        delay(50)

        assertEquals(listOf(AnalyticsEventRecord.Skipped("AccessibilityOff")), analytics.events)
        assertEquals(false, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }

    @Test
    fun maybeDrainReviewFlagKeepsPendingWhenEligibleButActivityIsNull() = runBlocking {
        val analytics = RecordingKeepAnalytics()
        val dataStore = pendingReviewDataStore()
        val launcher = FakeReviewLauncher()
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics, launcher = launcher)

        viewModel.maybeDrainReviewFlag(activity = null)
        delay(50)

        assertEquals(listOf(AnalyticsEventRecord.Skipped("NoActivity")), analytics.events)
        assertEquals(0, launcher.launchCount)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }

    @Test
    fun maybeDrainReviewFlagKeepsPendingWhenReviewLaunchFails() = runBlocking {
        val analytics = RecordingKeepAnalytics()
        val dataStore = pendingReviewDataStore()
        val launcher = FakeReviewLauncher(nextResult = ReviewLaunchResult.Failure("play_store_unavailable"))
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics, launcher = launcher)

        viewModel.maybeDrainReviewFlag(activity = Mockito.mock(android.app.Activity::class.java))
        delay(50)

        assertEquals(listOf(AnalyticsEventRecord.Failed("play_store_unavailable")), analytics.events)
        assertEquals(1, launcher.launchCount)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }

    private fun pendingReviewDataStore(): FakeDataStore =
        FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.REVIEW_PENDING to true,
            ),
        )

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: RecordingKeepAnalytics,
        accessibilityChecker: FakeAccessibilityChecker = FakeAccessibilityChecker(enabled = true),
        launcher: FakeReviewLauncher = FakeReviewLauncher(),
    ): HomeViewModel {
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        return HomeViewModel(
            dataStore = dataStore,
            blockingStateStore = BlockingStateStore(dataStore),
            reviewPromptStateStore = reviewPromptStateStore,
            routineNoticeStore = RoutineNoticeStore(dataStore),
            analytics = analytics,
            lockHistoryRecorder = LockHistoryRecorder(dataStore, LockHistoryRepository(FakeLockHistoryDao())),
            goalLockRepository = GoalLockRepository(EmptyGoalLockDao()),
            lockHistoryRepository = LockHistoryRepository(FakeLockHistoryDao()),
            routineRepository = EmptyHomeRoutineRepository(),
            repeatBlockSuggestionStore = RepeatBlockRoutineSuggestionStore(dataStore),
            reviewEligibility = ReviewEligibilityEvaluator(
                blockingStateStore = BlockingStateStore(dataStore),
                reviewPromptStateStore = reviewPromptStateStore,
                remoteConfig = FakeReviewRemoteConfig(enabled = true),
                accessibilityChecker = accessibilityChecker,
                repository = fakeReviewEligibilityRepository(recentSuccessCount = 2),
                clock = clock,
                buildConfig = ReviewBuildConfig(isDebug = false, flavor = "prod"),
            ),
            inAppReviewManager = InAppReviewManager(
                launcher = launcher,
                analytics = analytics,
                reviewPromptStateStore = reviewPromptStateStore,
                clock = clock,
            ),
        )
    }
}

private class EmptyHomeRoutineRepository : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(emptyList())
}

private class EmptyGoalLockDao : GoalLockDao {
    override fun fetchAll(): Flow<List<GoalLockEntity>> = flowOf(emptyList())

    override fun fetch(id: Long): GoalLockEntity? = null

    override fun insert(goalLock: GoalLockEntity): Long = goalLock.id

    override fun update(goalLock: GoalLockEntity) = Unit
}
