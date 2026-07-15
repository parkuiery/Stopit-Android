package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.RoutineCountAnalyticsSync
import com.uiery.keep.data.firstpromise.FirstPromiseCreationResult
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.data.lock.TimedLockSessionController
import com.uiery.keep.data.lockhistory.LockHistoryRepository
import com.uiery.keep.data.repeatblock.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.RecordingKeepAnalytics
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import com.uiery.keep.feature.review.fakeReviewEligibilityRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.service.LockHistoryRecorder
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelFirstPromiseResumeTest {
    @Test
    fun unavailablePermissionShowsCardOpensSettingsAndResumeFinalizesSameRoutine() = runBlocking {
        val fixture = fixture(canSchedule = false)
        val card = fixture.viewModel.awaitCard()
        assertEquals(42L, card.routineId)
        assertEquals("YouTube", card.appLabel)
        val sideEffect = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.viewModel.container.sideEffectFlow.first()
        }

        fixture.viewModel.activateFirstPromiseResumeCard()

        assertEquals(HomeSideEffect.OpenExactAlarmSettings, withTimeout(1_000) { sideEffect.await() })
        fixture.canSchedule.set(true)
        fixture.viewModel.onFirstPromiseExactAlarmResume()
        fixture.viewModel.awaitCardHidden()

        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
        assertEquals(42L, fixture.store.readState().routineId)
        assertEquals(FirstPromiseScheduleState.Enabled, fixture.store.readState().scheduleState)
    }

    @Test
    fun availablePermissionOnLoadFinalizesDirectlyWithoutOpeningSettings() = runBlocking {
        val fixture = fixture(canSchedule = true)

        withTimeout(1_000) { while (fixture.coordinator.finalizedIds.isEmpty()) yield() }

        assertNull(fixture.viewModel.container.stateFlow.value.firstPromiseResumeCard)
        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
    }

    @Test
    fun transientFailureKeepsRetryCardVisibleThroughRealHomeViewModel() = runBlocking {
        val fixture = fixture(canSchedule = true, finalizeFails = true)

        val card = fixture.viewModel.awaitCard()

        assertTrue(card.isRetry)
        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
    }

    @Test
    fun deletedMappingHidesCardAndSuccessfulRetryAlsoHidesIt() = runBlocking {
        val deleted = fixture(canSchedule = false, mappingExists = false)
        withTimeout(1_000) { while (deleted.coordinator.readMappingCalls == 0) yield() }
        assertNull(deleted.viewModel.container.stateFlow.value.firstPromiseResumeCard)

        val retry = fixture(canSchedule = true, finalizeFails = true)
        retry.viewModel.awaitCard()
        retry.coordinator.finalizeFails = false
        retry.viewModel.activateFirstPromiseResumeCard()
        retry.viewModel.awaitCardHidden()
        assertEquals(FirstPromiseScheduleState.Enabled, retry.store.readState().scheduleState)
    }

    @Test
    fun resumeCardDoesNotChangeExistingPrimaryCtaPriority() = runBlocking {
        val fixture = fixture(canSchedule = false)
        fixture.viewModel.awaitCard()

        val model = buildHomeStatusCtaModel(
            isKeep = false,
            selectedAppCount = 1,
            showFirstLockActivationCta = true,
            showRoutineCreationCta = false,
            hasGoalLockCard = true,
            hasFirstPromiseResumeCard = true,
        )

        assertEquals(HomeStatusKind.FIRST_LOCK_READY, model.statusKind)
        assertTrue(model.shouldToggleKeep)
        assertTrue(model.showGoalLockStatus)
    }
}

private data class HomeResumeFixture(
    val viewModel: HomeViewModel,
    val coordinator: FakeHomeRecoveryPersistence,
    val store: FirstPromiseDraftStore,
    val canSchedule: AtomicBoolean,
)

private fun fixture(
    canSchedule: Boolean,
    finalizeFails: Boolean = false,
    mappingExists: Boolean = true,
): HomeResumeFixture {
    val dataStore = FakeDataStore(
        mutablePreferencesOf(
            PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                FirstPromiseOnboardingState(
                    phase = FirstPromisePhase.CompletedDisabled,
                    routineId = 42L,
                    scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
                ),
            ),
        ),
    )
    val store = FirstPromiseDraftStore(dataStore)
    val persistence = FakeHomeRecoveryPersistence(store, finalizeFails, mappingExists)
    val availability = AtomicBoolean(canSchedule)
    val recovery = FirstPromiseHomeRecoveryCoordinator(
        draftStore = store,
        coordinator = persistence,
        canScheduleExactAlarms = availability::get,
    )
    val analytics = RecordingKeepAnalytics()
    val blockingStateStore = BlockingStateStore(dataStore)
    val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
    val routineRepository = ResumeEmptyRoutineRepository()
    val lockHistoryDao = FakeLockHistoryDao()
    val clock = Clock.fixed(Instant.parse("2026-05-11T14:30:00Z"), ZoneId.of("UTC"))
    val viewModel = HomeViewModel(
        dataStore = dataStore,
        blockingStateStore = blockingStateStore,
        reviewPromptStateStore = reviewPromptStateStore,
        routineNoticeStore = RoutineNoticeStore(dataStore),
        analytics = analytics,
        routineCountAnalyticsSync = RoutineCountAnalyticsSync(routineRepository, analytics),
        lockHistoryRecorder = LockHistoryRecorder(dataStore, LockHistorySessionWriter(lockHistoryDao)),
        goalLockRepository = GoalLockRepository(ResumeEmptyGoalLockDao()),
        lockHistoryRepository = LockHistoryRepository(lockHistoryDao),
        routineRepository = routineRepository,
        repeatBlockSuggestionStore = RepeatBlockRoutineSuggestionStore(dataStore),
        usageInsightRepository = homeUsageInsightRepository(dataStore),
        reviewEligibility = ReviewEligibilityEvaluator(
            blockingStateStore = blockingStateStore,
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
        timedLockStarter = TimedLockSessionController(blockingStateStore, analytics, clock),
        firstPromiseRecovery = recovery,
    )
    return HomeResumeFixture(viewModel, persistence, store, availability)
}

private suspend fun HomeViewModel.awaitCard(): FirstPromiseResumeCardState =
    withTimeout(1_000) { container.stateFlow.first { it.firstPromiseResumeCard != null } }
        .firstPromiseResumeCard!!

private suspend fun HomeViewModel.awaitCardHidden() {
    withTimeout(1_000) { container.stateFlow.first { it.firstPromiseResumeCard == null } }
}

private class FakeHomeRecoveryPersistence(
    private val store: FirstPromiseDraftStore,
    var finalizeFails: Boolean,
    private val mappingExists: Boolean,
) : FirstPromisePersistenceCoordinator {
    val finalizedIds = mutableListOf<Long>()
    var readMappingCalls: Int = 0

    override suspend fun persistCurrentDraft() = FirstPromisePersistenceResult.MissingDraft

    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? {
        readMappingCalls++
        return if (mappingExists) creation(enabled = false) else null
    }

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        if (mappingExists) FirstPromisePersistenceResult.Succeeded(creation(false))
        else FirstPromisePersistenceResult.MissingRoutine

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        finalizedIds += routineId
        if (finalizeFails) return FirstPromisePersistenceResult.Failed(IllegalStateException("schedule"))
        store.resolveScheduleState(routineId, FirstPromiseScheduleState.Enabled)
        return FirstPromisePersistenceResult.Succeeded(creation(true))
    }

    private fun creation(enabled: Boolean) = FirstPromiseCreationResult(
        routineId = 42L,
        routine = RoutineModel(
            id = 42L,
            name = "YouTube",
            startTime = LocalTime(23, 0),
            endTime = LocalTime(23, 30),
            repeatDays = "1010100",
            lockApplications = listOf("video.app"),
            isEnabled = enabled,
        ),
        scheduleState = if (enabled) {
            FirstPromiseScheduleState.Enabled
        } else {
            FirstPromiseScheduleState.DisabledExactAlarmMissing
        },
        schedulingSucceeded = enabled,
        created = false,
        draftId = "draft",
    )
}

private class ResumeEmptyRoutineRepository : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(emptyList())
    override suspend fun fetchAllOnce(): List<RoutineModel> = emptyList()
}

private class ResumeEmptyGoalLockDao : GoalLockDao {
    override fun fetchAll(): Flow<List<GoalLockEntity>> = flowOf(emptyList())
    override fun fetch(id: Long): GoalLockEntity? = null
    override fun insert(goalLock: GoalLockEntity): Long = goalLock.id
    override fun update(goalLock: GoalLockEntity) = Unit
    override fun markEndedEarlyIfActive(id: Long): Int = 0
    override fun markCompletedIfActiveAndEndDate(id: Long, expectedEndDate: String): Int = 0
}
