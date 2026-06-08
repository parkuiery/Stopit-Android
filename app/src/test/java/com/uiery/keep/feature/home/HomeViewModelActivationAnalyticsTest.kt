package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsGoalLockDurationDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.goallock.GoalLock
import com.uiery.keep.feature.goallock.GoalLockMode
import com.uiery.keep.feature.goallock.GoalLockRepository
import com.uiery.keep.feature.goallock.GoalLockStoredStatus
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.feature.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.fakeReviewEligibilityRepository
import com.uiery.keep.service.LockHistoryRecorder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelActivationAnalyticsTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-11T14:30:00Z"), ZoneId.of("UTC"))

    @Test
    fun changeIsKeepTracksFirstLockConfiguredFromHomeOnce() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one", "com.example.two"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        viewModel.changeIsKeep()
        delay(50)

        assertEquals(
            listOf(
                HomeAnalyticsCall.KeepModeToggled(isEnabled = true),
                HomeAnalyticsCall.FirstLockConfigured(
                    source = AnalyticsSource.HOME,
                    selectedAppCount = 2,
                ),
                HomeAnalyticsCall.LockSessionStarted(
                    source = AnalyticsSource.HOME_KEEP_SWITCH,
                    isRoutine = false,
                ),
            ),
            analytics.calls,
        )
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun changeIsKeepShowsFirstLockGuidanceOnlyWhenFirstLockIsConfigured() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(50)
        viewModel.changeIsKeep(firstLockStartedMessage = "선택한 앱을 열면 첫 차단을 확인할 수 있어요")
        delay(50)
        viewModel.changeIsKeep(firstLockStartedMessage = "선택한 앱을 열면 첫 차단을 확인할 수 있어요")
        delay(50)
        viewModel.changeIsKeep(firstLockStartedMessage = "선택한 앱을 열면 첫 차단을 확인할 수 있어요")
        delay(50)

        assertEquals(
            listOf(HomeSideEffect.ShowSnackBar("선택한 앱을 열면 첫 차단을 확인할 수 있어요")),
            sideEffects,
        )
        sideEffectJob.cancel()
    }

    @Test
    fun lockTimeTracksFirstLockConfiguredFromHomeTimerOnce() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.lockTime()
        delay(50)

        val storedLockTime = dataStore.snapshot()[PreferencesKey.LOCK_TIME]

        assertEquals(HomeAnalyticsCall.FirstLockConfigured(AnalyticsSource.HOME_TIMER, 1), analytics.calls[0])
        assertEquals(HomeAnalyticsCall.LockScheduled(AnalyticsScheduleType.TIMER), analytics.calls[1])
        assertEquals(true, runCatching { Instant.parse(storedLockTime) }.isSuccess)
        assertEquals(
            true,
            ManualLockTimePolicy.isActiveAt(storedLockTime),
        )
        assertEquals(
            HomeAnalyticsCall.LockSessionStarted(
                source = AnalyticsSource.HOME_TIMER,
                isRoutine = false,
            ),
            analytics.calls[2],
        )
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun lockTimePersistsManualTimerSessionStartTimeWithDeadline() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
                PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        val before = System.currentTimeMillis()
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.lockTime()
        delay(50)
        val after = System.currentTimeMillis()

        val snapshot = dataStore.snapshot()
        val storedStartTime = snapshot[PreferencesKey.START_TIME]

        assertEquals(true, ManualLockTimePolicy.isActiveAt(snapshot[PreferencesKey.LOCK_TIME]))
        assertEquals(true, storedStartTime != null)
        assertEquals(true, storedStartTime!! in before..after)
    }

    @Test
    fun lockTimeUsesTimerScheduleAfterCountdownValueWhenTimerModeIsSelected() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
                PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.updateCountdownDuration(CountdownDuration(day = 1, hour = 0, minute = 0))
        viewModel.updateManualLockMode(ManualLockMode.TIMER)
        viewModel.lockTime()
        delay(50)

        assertEquals(ManualLockMode.TIMER, viewModel.container.stateFlow.value.manualLockMode)
        assertEquals(HomeAnalyticsCall.LockScheduled(AnalyticsScheduleType.TIMER), analytics.calls[0])
        assertEquals(true, ManualLockTimePolicy.isActiveAt(dataStore.snapshot()[PreferencesKey.LOCK_TIME]))
    }

    @Test
    fun switchingToTimerModeDoesNotClearCountdownValueButExposesTimerModeForUiDecisions() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        viewModel.updateCountdownDuration(CountdownDuration(day = 1, hour = 0, minute = 0))
        viewModel.updateManualLockMode(ManualLockMode.TIMER)
        delay(50)

        val state = viewModel.container.stateFlow.value
        assertEquals(1, state.countdownDays)
        assertEquals(ManualLockMode.TIMER, state.manualLockMode)
    }

    @Test
    fun lockTimeShowsScheduledFirstLockGuidanceOnlyWhenFirstLockIsConfigured() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(50)
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.lockTime(firstLockScheduledMessage = "예약 시간이 되면 선택한 앱 차단으로 첫 성공을 확인해요")
        delay(50)
        viewModel.lockTime(firstLockScheduledMessage = "예약 시간이 되면 선택한 앱 차단으로 첫 성공을 확인해요")
        delay(50)

        assertEquals(
            listOf(HomeSideEffect.ShowSnackBar("예약 시간이 되면 선택한 앱 차단으로 첫 성공을 확인해요")),
            sideEffects,
        )
        sideEffectJob.cancel()
    }

    @Test
    fun changeIsKeepPromptsAppSelectionInsteadOfTrackingFirstLockWhenNoAppsSelected() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(50)
        viewModel.changeIsKeep(noSelectedAppsMessage = "앱을 먼저 선택해 주세요")
        delay(50)

        assertEquals(emptyList<HomeAnalyticsCall>(), analytics.calls)
        assertEquals(null, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
        assertEquals(false, viewModel.container.stateFlow.value.isKeep)
        assertEquals(true, viewModel.container.stateFlow.value.isShowCategoryBottomSheet)
        assertEquals(HomeSideEffect.ShowSnackBar("앱을 먼저 선택해 주세요"), sideEffects.single())
        sideEffectJob.cancel()
    }

    @Test
    fun lockTimePromptsAppSelectionInsteadOfTrackingFirstLockWhenNoAppsSelected() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(50)
        viewModel.lockTime(noSelectedAppsMessage = "앱을 먼저 선택해 주세요")
        delay(50)

        assertEquals(emptyList<HomeAnalyticsCall>(), analytics.calls)
        assertEquals(null, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
        assertEquals(null, dataStore.snapshot()[PreferencesKey.LOCK_TIME])
        assertEquals(true, viewModel.container.stateFlow.value.isShowCategoryBottomSheet)
        assertEquals(HomeSideEffect.ShowSnackBar("앱을 먼저 선택해 주세요"), sideEffects.single())
        sideEffectJob.cancel()
    }

    @Test
    fun selectedAppsWithoutFirstLockExposeActivationCta() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)

        assertEquals(true, viewModel.container.stateFlow.value.showFirstLockActivationCta)
    }

    @Test
    fun firstLockActivationCtaStaysHiddenWithoutSelectedApps() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)

        assertEquals(false, viewModel.container.stateFlow.value.showFirstLockActivationCta)
    }

    @Test
    fun firstLockActivationCtaStaysHiddenAfterFirstLockConfigured() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
                PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)

        assertEquals(false, viewModel.container.stateFlow.value.showFirstLockActivationCta)
    }

    @Test
    fun selectingAppsBeforeFirstLockExposesActivationCta() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        viewModel.selectCategoryComplete(setOf("com.example.one"))
        delay(50)

        assertEquals(true, viewModel.container.stateFlow.value.showFirstLockActivationCta)
    }

    @Test
    fun firstLockActivationCtaHidesAfterHomeKeepStartsFirstLock() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        delay(50)
        assertEquals(true, viewModel.container.stateFlow.value.showFirstLockActivationCta)
        viewModel.changeIsKeep()
        delay(50)

        assertEquals(false, viewModel.container.stateFlow.value.showFirstLockActivationCta)
    }

    @Test
    fun moveToLockUsesTheSameDeadlinePersistedByLockTimeEvenIfTimerStateChangesBeforeNavigation() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(50)
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.lockTime()
        delay(50)
        val persistedDeadline = dataStore.snapshot()[PreferencesKey.LOCK_TIME]

        viewModel.updateTimerTime(LocalTime(hour = 22, minute = 10))
        viewModel.moveToLock()
        delay(50)

        assertEquals(
            HomeSideEffect.MoveToLock(lockTime = persistedDeadline, isRoutine = false),
            sideEffects.filterIsInstance<HomeSideEffect.MoveToLock>().single(),
        )
        sideEffectJob.cancel()
    }

    @Test
    fun lockTimeDoesNotPreRecordFutureTimerSessionInHistoryLedger() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.SELECTED_APP_PACKAGES to setOf("com.example.one"),
                PreferencesKey.TOTAL_BLOCK_TIME to 9_000L,
                PreferencesKey.LONG_BLOCK_TIME to 4_000L,
            ),
        )
        val lockHistoryDao = HomeRecordingLockHistoryDao()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = lockHistoryDao,
        )

        delay(50)
        viewModel.updateTimerTime(LocalTime(hour = 23, minute = 45))
        viewModel.lockTime()
        delay(50)

        assertEquals("예약만으로는 완료된 잠금 세션을 Room 원장에 적재하지 않아야 합니다.", emptyList<LockHistoryEntity>(), lockHistoryDao.inserted)
        assertEquals(9_000L, dataStore.snapshot()[PreferencesKey.TOTAL_BLOCK_TIME])
        assertEquals(4_000L, dataStore.snapshot()[PreferencesKey.LONG_BLOCK_TIME])
    }

    @Test
    fun repeatedBlockPatternExposesRoutineSuggestionAndTracksShownOnce() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val now = System.currentTimeMillis()
        val lockHistoryDao = HomeRecordingLockHistoryDao(
            sessions = listOf(
                lockHistoryEntity(now - 1_000L, listOf("com.instagram.android")),
                lockHistoryEntity(now - 86_400_000L, listOf("com.instagram.android")),
                lockHistoryEntity(now - 172_800_000L, listOf("com.instagram.android")),
            ),
        )
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = lockHistoryDao,
            routineRepository = FakeHomeRoutineRepository(emptyList()),
        )

        delay(100)

        val suggestion = viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion
        assertEquals(true, suggestion != null)
        assertEquals(
            listOf(HomeAnalyticsCall.RepeatBlockSuggestionShown(surface = "home")),
            analytics.calls.filterIsInstance<HomeAnalyticsCall.RepeatBlockSuggestionShown>(),
        )
    }

    @Test
    fun dismissingRepeatedBlockRoutineSuggestionHidesItAndPersistsSuppression() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val now = System.currentTimeMillis()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = HomeRecordingLockHistoryDao(
                sessions = listOf(
                    lockHistoryEntity(now - 1_000L, listOf("com.instagram.android")),
                    lockHistoryEntity(now - 86_400_000L, listOf("com.instagram.android")),
                    lockHistoryEntity(now - 172_800_000L, listOf("com.instagram.android")),
                ),
            ),
            routineRepository = FakeHomeRoutineRepository(emptyList()),
        )

        delay(100)
        viewModel.dismissRepeatBlockRoutineSuggestion()
        delay(100)

        assertEquals(null, viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion)
        assertEquals(
            listOf(HomeAnalyticsCall.RepeatBlockSuggestionDismissed(surface = "home")),
            analytics.calls.filterIsInstance<HomeAnalyticsCall.RepeatBlockSuggestionDismissed>(),
        )
        assertEquals(1, RepeatBlockRoutineSuggestionStore(dataStore).readDismissedSuggestions().size)
    }

    @Test
    fun clickingRepeatedBlockRoutineSuggestionPostsRoutinePrefillNavigation() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val now = System.currentTimeMillis()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryDao = HomeRecordingLockHistoryDao(
                sessions = listOf(
                    lockHistoryEntity(now - 1_000L, listOf("com.instagram.android")),
                    lockHistoryEntity(now - 86_400_000L, listOf("com.instagram.android")),
                    lockHistoryEntity(now - 172_800_000L, listOf("com.instagram.android")),
                ),
            ),
            routineRepository = FakeHomeRoutineRepository(emptyList()),
        )
        val sideEffects = mutableListOf<HomeSideEffect>()
        val sideEffectJob = launchSideEffects(viewModel, sideEffects)

        delay(100)
        viewModel.openRepeatBlockRoutineSuggestion()
        delay(100)

        assertEquals(
            HomeSideEffect.NavigateToRoutineWithRepeatBlockPrefill(
                requireNotNull(viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion),
            ),
            sideEffects.filterIsInstance<HomeSideEffect.NavigateToRoutineWithRepeatBlockPrefill>().single(),
        )
        sideEffectJob.cancel()
    }

    @Test
    fun activeGoalLockExposesHomeProgressCardState() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val dataStore = FakeDataStore(mutablePreferencesOf())
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            goalLockDao = FakeHomeGoalLockDao(
                listOf(
                    goalLockEntity(
                        goalName = "시험 준비",
                        startDate = LocalDate.now().minusDays(1),
                        endDate = LocalDate.now().plusDays(13),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.video.app", "com.social.app"),
                    ),
                ),
            ),
        )

        delay(50)

        assertEquals(
            HomeGoalLockCardState(
                goalLockId = 7L,
                goalName = "시험 준비",
                status = HomeGoalLockStatus.Active,
                daysRemaining = 14,
                lockModeLabel = "하루종일 잠금",
                selectedAppCount = 2,
            ),
            viewModel.container.stateFlow.value.goalLockCard,
        )
    }

    @Test
    fun activeGoalLockTakesPriorityOverFuturePendingGoalLockOnHomeCard() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            dataStore = FakeDataStore(mutablePreferencesOf()),
            analytics = analytics,
            goalLockDao = FakeHomeGoalLockDao(
                listOf(
                    goalLockEntity(
                        id = 20L,
                        goalName = "다음 시험 준비",
                        startDate = today.plusDays(7),
                        endDate = today.plusDays(14),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.future.app"),
                    ),
                    goalLockEntity(
                        id = 10L,
                        goalName = "현재 시험 준비",
                        startDate = today.minusDays(1),
                        endDate = today.plusDays(5),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.active.app"),
                    ),
                ),
            ),
        )

        delay(50)

        assertEquals(
            HomeGoalLockCardState(
                goalLockId = 10L,
                goalName = "현재 시험 준비",
                status = HomeGoalLockStatus.Active,
                daysRemaining = 6,
                lockModeLabel = "하루종일 잠금",
                selectedAppCount = 1,
            ),
            viewModel.container.stateFlow.value.goalLockCard,
        )
    }

    @Test
    fun nearestPendingGoalLockIsShownWhenNoGoalLockIsCurrentlyActive() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            dataStore = FakeDataStore(mutablePreferencesOf()),
            analytics = analytics,
            goalLockDao = FakeHomeGoalLockDao(
                listOf(
                    goalLockEntity(
                        id = 30L,
                        goalName = "먼 시험 준비",
                        startDate = today.plusDays(20),
                        endDate = today.plusDays(25),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.far.app"),
                    ),
                    goalLockEntity(
                        id = 31L,
                        goalName = "가까운 시험 준비",
                        startDate = today.plusDays(2),
                        endDate = today.plusDays(8),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.near.app"),
                    ),
                ),
            ),
        )

        delay(50)

        assertEquals(
            HomeGoalLockCardState(
                goalLockId = 31L,
                goalName = "가까운 시험 준비",
                status = HomeGoalLockStatus.Pending,
                daysRemaining = 9,
                lockModeLabel = "하루종일 잠금",
                selectedAppCount = 1,
            ),
            viewModel.container.stateFlow.value.goalLockCard,
        )
    }

    @Test
    fun completedGoalLockDoesNotHideActiveOrPendingHomeCardCandidate() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val today = LocalDate.now()
        val viewModel = createViewModel(
            dataStore = FakeDataStore(mutablePreferencesOf()),
            analytics = analytics,
            goalLockDao = FakeHomeGoalLockDao(
                listOf(
                    goalLockEntity(
                        id = 40L,
                        goalName = "이미 끝난 시험 준비",
                        startDate = today.minusDays(20),
                        endDate = today.minusDays(10),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.done.app"),
                        status = GoalLockStoredStatus.Completed,
                    ),
                    goalLockEntity(
                        id = 41L,
                        goalName = "곧 시작할 시험 준비",
                        startDate = today.plusDays(1),
                        endDate = today.plusDays(5),
                        lockMode = GoalLockMode.AllDay,
                        selectedPackages = setOf("com.pending.app"),
                    ),
                ),
            ),
        )

        delay(50)

        assertEquals(
            HomeGoalLockCardState(
                goalLockId = 41L,
                goalName = "곧 시작할 시험 준비",
                status = HomeGoalLockStatus.Pending,
                daysRemaining = 6,
                lockModeLabel = "하루종일 잠금",
                selectedAppCount = 1,
            ),
            viewModel.container.stateFlow.value.goalLockCard,
        )
    }

    @Test
    fun expiredActiveGoalLockIsCompletedFromHomeCardLoadAndTrackedOnce() = runBlocking {
        val analytics = HomeRecordingKeepAnalytics()
        val goalLockDao = FakeHomeGoalLockDao(
            listOf(
                goalLockEntity(
                    goalName = "30일 SNS 줄이기",
                    startDate = LocalDate.now().minusDays(30),
                    endDate = LocalDate.now().minusDays(1),
                    lockMode = GoalLockMode.AllDay,
                    selectedPackages = setOf("com.social.app"),
                ),
            ),
        )
        val viewModel = createViewModel(
            dataStore = FakeDataStore(mutablePreferencesOf()),
            analytics = analytics,
            goalLockDao = goalLockDao,
        )

        delay(50)

        assertEquals(
            HomeGoalLockCardState(
                goalLockId = 7L,
                goalName = "30일 SNS 줄이기",
                status = HomeGoalLockStatus.Completed,
                daysRemaining = 0,
                lockModeLabel = "하루종일 잠금",
                selectedAppCount = 1,
            ),
            viewModel.container.stateFlow.value.goalLockCard,
        )
        assertEquals(GoalLockStoredStatus.Completed, goalLockDao.updated.single().toDomain().status)
        assertEquals(
            listOf(
                HomeAnalyticsCall.GoalLockCompleted(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    durationDaysBucket = AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY,
                ),
            ),
            analytics.calls,
        )
    }

    private fun CoroutineScope.launchSideEffects(
        viewModel: HomeViewModel,
        sideEffects: MutableList<HomeSideEffect>,
    ): Job = launch {
        viewModel.container.sideEffectFlow.collect { sideEffect ->
            sideEffects += sideEffect
        }
    }

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: HomeRecordingKeepAnalytics,
        lockHistoryDao: LockHistoryDao = FakeLockHistoryDao(),
        goalLockDao: GoalLockDao = FakeHomeGoalLockDao(),
        routineRepository: RoutineRepository = FakeHomeRoutineRepository(emptyList()),
    ): HomeViewModel {
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        return HomeViewModel(
            dataStore = dataStore,
            blockingStateStore = BlockingStateStore(dataStore),
            reviewPromptStateStore = reviewPromptStateStore,
            routineNoticeStore = RoutineNoticeStore(dataStore),
            analytics = analytics,
            lockHistoryRecorder = LockHistoryRecorder(dataStore, LockHistorySessionWriter(lockHistoryDao)),
            goalLockRepository = GoalLockRepository(goalLockDao),
            lockHistoryRepository = LockHistoryRepository(lockHistoryDao),
            routineRepository = routineRepository,
            repeatBlockSuggestionStore = RepeatBlockRoutineSuggestionStore(dataStore),
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

private class FakeHomeGoalLockDao(
    private val goalLocks: List<GoalLockEntity> = emptyList(),
) : GoalLockDao {
    val updated = mutableListOf<GoalLockEntity>()

    override fun fetchAll(): Flow<List<GoalLockEntity>> = flowOf(goalLocks)

    override fun fetch(id: Long): GoalLockEntity? = goalLocks.firstOrNull { it.id == id }

    override fun insert(goalLock: GoalLockEntity): Long = goalLock.id

    override fun update(goalLock: GoalLockEntity) {
        updated += goalLock
    }
}

private fun goalLockEntity(
    goalName: String,
    startDate: LocalDate,
    endDate: LocalDate,
    lockMode: GoalLockMode,
    selectedPackages: Set<String>,
    id: Long = 7L,
    status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
): GoalLockEntity = GoalLockEntity.fromDomain(
    GoalLock(
        id = id,
        goalName = goalName,
        startDate = startDate,
        endDate = endDate,
        lockMode = lockMode,
        selectedPackages = selectedPackages,
        status = status,
    ),
)

private class HomeRecordingLockHistoryDao(
    private val sessions: List<LockHistoryEntity> = emptyList(),
) : LockHistoryDao {
    val inserted = mutableListOf<LockHistoryEntity>()

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> =
        flowOf(sessions.filter { it.startTimestamp in startMillis..endMillis })

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(sessions)

    override suspend fun countSuccessfulSessions(): Int = inserted.size + sessions.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        (inserted + sessions).count { it.startTimestamp >= timestampMillis }
}

private class FakeHomeRoutineRepository(
    private val routines: List<RoutineModel>,
) : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(routines)
}

private fun lockHistoryEntity(
    startTimestamp: Long,
    lockedApps: List<String>,
): LockHistoryEntity = LockHistoryEntity(
    startTimestamp = startTimestamp,
    endTimestamp = startTimestamp + 30 * 60 * 1_000L,
    durationMillis = 30 * 60 * 1_000L,
    lockedApps = lockedApps,
    isRoutine = false,
)

private sealed interface HomeAnalyticsCall {
    data class FirstLockConfigured(
        val source: String,
        val selectedAppCount: Int?,
    ) : HomeAnalyticsCall

    data class LockSessionStarted(
        val source: String,
        val isRoutine: Boolean?,
    ) : HomeAnalyticsCall

    data class LockSessionEnded(
        val source: String,
        val endReason: String,
        val isRoutine: Boolean?,
    ) : HomeAnalyticsCall

    data class KeepModeToggled(
        val isEnabled: Boolean,
    ) : HomeAnalyticsCall

    data class LockScheduled(
        val scheduleType: String,
    ) : HomeAnalyticsCall

    data class GoalLockCompleted(
        val lockMode: String,
        val durationDaysBucket: String,
    ) : HomeAnalyticsCall

    data class RepeatBlockSuggestionShown(
        val surface: String,
    ) : HomeAnalyticsCall

    data class RepeatBlockSuggestionDismissed(
        val surface: String,
    ) : HomeAnalyticsCall

    data class RepeatBlockSuggestionClicked(
        val surface: String,
    ) : HomeAnalyticsCall
}

private class HomeRecordingKeepAnalytics : KeepAnalytics {
    val calls = mutableListOf<HomeAnalyticsCall>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) {
        calls += HomeAnalyticsCall.FirstLockConfigured(source = source, selectedAppCount = selectedAppCount)
    }

    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) {
        calls += HomeAnalyticsCall.LockSessionStarted(source = source, isRoutine = isRoutine)
    }

    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) {
        calls += HomeAnalyticsCall.LockSessionEnded(
            source = source,
            endReason = endReason,
            isRoutine = isRoutine,
        )
    }

    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackKeepModeToggled(isEnabled: Boolean) {
        calls += HomeAnalyticsCall.KeepModeToggled(isEnabled = isEnabled)
    }

    override fun trackLockScheduled(scheduleType: String, scheduledDurationMinutes: Long) {
        calls += HomeAnalyticsCall.LockScheduled(scheduleType = scheduleType)
    }

    override fun trackGoalLockCompleted(lockMode: String, durationDaysBucket: String) {
        calls += HomeAnalyticsCall.GoalLockCompleted(
            lockMode = lockMode,
            durationDaysBucket = durationDaysBucket,
        )
    }

    override fun trackRepeatBlockRoutineSuggestionShown(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestion,
    ) {
        calls += HomeAnalyticsCall.RepeatBlockSuggestionShown(surface = surface)
    }

    override fun trackRepeatBlockRoutineSuggestionClicked(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestion,
    ) {
        calls += HomeAnalyticsCall.RepeatBlockSuggestionClicked(surface = surface)
    }

    override fun trackRepeatBlockRoutineSuggestionDismissed(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestion,
    ) {
        calls += HomeAnalyticsCall.RepeatBlockSuggestionDismissed(surface = surface)
    }
}
