package com.uiery.keep

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.AnalyticsParentModeBlockContext
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.BlockAnalyticsCoordinator
import com.uiery.keep.analytics.FirstCoreActionDeliveryCoordinator
import com.uiery.keep.analytics.FirstCoreActionMarker
import com.uiery.keep.analytics.FirstCoreActionMarkerState
import com.uiery.keep.analytics.FirstCoreActionReservationStore
import com.uiery.keep.analytics.KeepBlockDirectAnalyticsDelivery
import com.uiery.keep.analytics.NoAttributionStore
import com.uiery.keep.analytics.NoOpOutboxDispatcher
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.data.lockhistory.LockHistoryRepository
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.data.repeatblock.RepeatBlockRoutineSuggestionStore
import com.uiery.keep.domain.repeatblock.AppCategoryResolver
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.data.emergencyunlock.EmergencyUnlockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BlockViewModelTest {
    @Test
    fun initLogsBlockScreenView() {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore()

        createViewModel(dataStore = dataStore, analytics = analytics)

        assertEquals(listOf(KeepAnalyticsScreen.BLOCK), analytics.screenViews)
    }

    @Test
    fun firstBlockShowsFirstCoreActionFeedbackAndTracksFunnelOrder() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_OPEN_TIMESTAMP to 1_000L,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        viewModel.trackBlockShown(
            packageName = "com.example.blocked",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            routineId = null,
        )
        delay(50)

        assertEquals(true, viewModel.container.stateFlow.value.showFirstCoreActionFeedback)
        assertEquals(
            listOf(
                BlockAnalyticsCall.AppBlockIntercepted(
                    blockSource = AnalyticsBlockSource.MANUAL_KEEP,
                    blockedAppPackage = "com.example.blocked",
                    routineId = null,
                ),
                BlockAnalyticsCall.FirstCoreActionCompleted(
                    blockingMode = AnalyticsBlockSource.MANUAL_KEEP,
                    blockedAppPackage = "com.example.blocked",
                    routineId = null,
                ),
            ),
            analytics.calls,
        )
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION])
    }

    @Test
    fun repeatBlockDoesNotShowFirstCoreActionFeedback() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        viewModel.trackBlockShown(
            packageName = "com.example.blocked",
            blockSource = AnalyticsBlockSource.ROUTINE,
            routineId = "routine-1",
        )
        delay(50)

        assertEquals(false, viewModel.container.stateFlow.value.showFirstCoreActionFeedback)
        assertEquals(
            listOf(
                BlockAnalyticsCall.AppBlockIntercepted(
                    blockSource = AnalyticsBlockSource.ROUTINE,
                    blockedAppPackage = "com.example.blocked",
                    routineId = "routine-1",
                ),
                BlockAnalyticsCall.CoreActionCompleted(
                    blockingMode = AnalyticsBlockSource.ROUTINE,
                    blockedAppPackage = "com.example.blocked",
                    routineId = "routine-1",
                ),
            ),
            analytics.calls,
        )
    }

    @Test
    fun goalLockBlockTracksGoalLockSourceAndId() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        viewModel.trackBlockShown(
            packageName = "com.example.goal",
            blockSource = AnalyticsBlockSource.GOAL_LOCK,
            routineId = null,
            goalLockId = "77",
        )
        awaitUntil { analytics.calls.size == 2 }

        assertEquals(
            listOf(
                BlockAnalyticsCall.AppBlockIntercepted(
                    blockSource = AnalyticsBlockSource.GOAL_LOCK,
                    blockedAppPackage = "com.example.goal",
                    routineId = null,
                    goalLockId = "77",
                ),
                BlockAnalyticsCall.CoreActionCompleted(
                    blockingMode = AnalyticsBlockSource.GOAL_LOCK,
                    blockedAppPackage = "com.example.goal",
                    routineId = null,
                    goalLockId = "77",
                ),
            ),
            analytics.calls,
        )
    }

    @Test
    fun parentModeBlockTracksDedicatedPrivacySafeInterceptEvent() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        viewModel.trackBlockShown(
            packageName = "com.example.child.disallowed",
            blockSource = AnalyticsBlockSource.PARENT_MODE,
            routineId = null,
        )
        delay(50)

        assertEquals(
            listOf(
                BlockAnalyticsCall.AppBlockIntercepted(
                    blockSource = AnalyticsBlockSource.PARENT_MODE,
                    blockedAppPackage = "com.example.child.disallowed",
                    routineId = null,
                ),
                BlockAnalyticsCall.ParentModeBlockIntercepted(
                    blockContext = AnalyticsParentModeBlockContext.DISALLOWED_APP,
                ),
                BlockAnalyticsCall.CoreActionCompleted(
                    blockingMode = AnalyticsBlockSource.PARENT_MODE,
                    blockedAppPackage = "com.example.child.disallowed",
                    routineId = null,
                ),
            ),
            analytics.calls,
        )
    }

    @Test
    fun disabledEmergencyUnlockStateDoesNotLookLikeDailyLimitReached() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.EMERGENCY_UNLOCK_ENABLED to false,
                PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT to 3,
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = BlockRecordingKeepAnalytics())

        delay(50)

        val state = viewModel.container.stateFlow.value
        assertEquals(false, state.emergencyUnlockEnabled)
        assertEquals(com.uiery.keep.service.EmergencyUnlockAvailabilityReason.Disabled, state.emergencyUnlockAvailabilityReason)
        assertEquals(false, state.dailyLimitReached)
        assertEquals(3, state.dailyUnlockRemaining)
    }

    @Test
    fun timedLockReentryLoadsActiveDeadlineForCountdown() = runBlocking {
        val zone = ZoneId.systemDefault()
        val deadline = Instant.parse("2035-01-01T00:00:00Z")
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.LOCK_TIME to ManualLockTimePolicy.encodeDeadline(deadline),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = BlockRecordingKeepAnalytics())

        viewModel.syncManualTimedLockReentry(
            blockSource = AnalyticsBlockSource.TIMED_LOCK,
            now = Instant.parse("2034-12-31T23:59:00Z"),
            zone = zone,
        )
        delay(50)

        assertEquals(deadline.atZone(zone).toLocalDateTime(), viewModel.container.stateFlow.value.timedLockDeadline)
    }

    @Test
    fun timedLockReentryEmitsCloseWhenStoredDeadlineAlreadyExpired() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.LOCK_TIME to ManualLockTimePolicy.encodeDeadline(Instant.parse("2035-01-01T00:00:00Z")),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = BlockRecordingKeepAnalytics())
        val sideEffects = mutableListOf<BlockSideEffect>()
        val job = this.launch {
            viewModel.container.sideEffectFlow.collect { sideEffects += it }
        }

        viewModel.syncManualTimedLockReentry(
            blockSource = AnalyticsBlockSource.TIMED_LOCK,
            now = Instant.parse("2035-01-01T00:00:01Z"),
        )
        delay(50)
        job.cancel()

        assertEquals(listOf(BlockSideEffect.TimedLockExpired), sideEffects)
        assertEquals(null, viewModel.container.stateFlow.value.timedLockDeadline)
    }

    @Test
    fun routineBlockDoesNotReuseManualTimedLockCountdown() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.LOCK_TIME to ManualLockTimePolicy.encodeDeadline(Instant.parse("2035-01-01T00:00:00Z")),
            ),
        )
        val viewModel = createViewModel(dataStore = dataStore, analytics = BlockRecordingKeepAnalytics())

        viewModel.syncManualTimedLockReentry(
            blockSource = AnalyticsBlockSource.ROUTINE,
            now = Instant.parse("2034-12-31T23:59:00Z"),
        )
        delay(50)

        assertEquals(null, viewModel.container.stateFlow.value.timedLockDeadline)
    }

    @Test
    fun emergencyUnlockStepAnalyticsUseBlockScreenSource() {
        val analytics = BlockRecordingKeepAnalytics()
        val viewModel = createViewModel(dataStore = FakeDataStore(), analytics = analytics)

        viewModel.trackEmergencyUnlockStepViewed("apps")
        viewModel.trackEmergencyUnlockValidationBlocked("apps", "missing_app_selection")
        viewModel.trackEmergencyUnlockCancelled("countdown")

        assertEquals(
            listOf(
                BlockAnalyticsCall.EmergencyUnlockStepViewed("apps", true),
                BlockAnalyticsCall.EmergencyUnlockValidationBlocked("apps", "missing_app_selection", true),
                BlockAnalyticsCall.EmergencyUnlockCancelled("countdown", true),
            ),
            analytics.calls,
        )
    }

    @Test
    fun postBlockSuccessShowsRepeatBlockSuggestionWithPrivacySafeSurface() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val now = LocalDateTime.now()
        val rapidRetryAnchor = now
            .minusDays(1)
            .withHour(15)
            .withMinute(30)
            .withSecond(0)
            .withNano(0)
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        lockHistoryAt(rapidRetryAnchor.minusMinutes(2), "com.instagram.android"),
                        lockHistoryAt(rapidRetryAnchor.minusMinutes(4), "com.instagram.android"),
                        lockHistoryAt(rapidRetryAnchor.minusMinutes(6), "com.instagram.android"),
                        lockHistoryAt(rapidRetryAnchor.minusDays(1), "com.instagram.android"),
                    ),
                ),
            ),
        )

        viewModel.trackBlockShown(
            packageName = "com.instagram.android",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            routineId = null,
        )
        awaitUntil { analytics.repeatBlockEvents.isNotEmpty() }

        assertNotNull(viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion)
        assertEquals(
            listOf(RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS),
            analytics.repeatBlockEvents.map { it.surface },
        )
        assertEquals("rapid_retry", analytics.repeatBlockEvents.single().payload.reason)
        assertEquals("social", analytics.repeatBlockEvents.single().payload.categoryBucket)
    }

    @Test
    fun goalLockBlockSuppressesPostBlockSuccessRepeatBlockSuggestion() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val now = LocalDateTime.now()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        lockHistoryAt(now.minusMinutes(2), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(4), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(6), "com.instagram.android"),
                        lockHistoryAt(now.minusDays(1), "com.instagram.android"),
                    ),
                ),
            ),
        )

        viewModel.trackBlockShown(
            packageName = "com.instagram.android",
            blockSource = AnalyticsBlockSource.GOAL_LOCK,
            routineId = null,
            goalLockId = "goal-1",
        )
        delay(100)

        assertNull(viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion)
        assertEquals(emptyList<RepeatBlockAnalyticsCall>(), analytics.repeatBlockEvents)
    }

    @Test
    fun dismissPostBlockSuccessRepeatBlockSuggestionStoresBucketAndTracksDismissed() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val now = LocalDateTime.now()
        val repeatBlockSuggestionStore = RepeatBlockRoutineSuggestionStore(dataStore)
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            repeatBlockSuggestionStore = repeatBlockSuggestionStore,
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        lockHistoryAt(now.minusMinutes(2), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(4), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(6), "com.instagram.android"),
                        lockHistoryAt(now.minusDays(1), "com.instagram.android"),
                    ),
                ),
            ),
        )

        viewModel.trackBlockShown(
            packageName = "com.instagram.android",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            routineId = null,
        )
        delay(100)

        viewModel.dismissRepeatBlockRoutineSuggestion()
        delay(50)

        assertEquals(null, viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion)
        assertEquals(
            listOf(
                "repeat_block_shown" to RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                "repeat_block_dismissed" to RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
            ),
            analytics.repeatBlockEvents.map { it.name to it.surface },
        )
        assertEquals(1, repeatBlockSuggestionStore.readDismissedSuggestions().size)
    }

    @Test
    fun openPostBlockSuccessRepeatBlockSuggestionTracksClickAndEmitsRoutinePrefillEffect() = runBlocking {
        val analytics = BlockRecordingKeepAnalytics()
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION to true,
            ),
        )
        val now = LocalDateTime.now()
        val viewModel = createViewModel(
            dataStore = dataStore,
            analytics = analytics,
            lockHistoryRepository = LockHistoryRepository(
                LockHistoryDaoWithSessions(
                    listOf(
                        lockHistoryAt(now.minusMinutes(2), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(4), "com.instagram.android"),
                        lockHistoryAt(now.minusMinutes(6), "com.instagram.android"),
                        lockHistoryAt(now.minusDays(1), "com.instagram.android"),
                    ),
                ),
            ),
        )
        val sideEffects = mutableListOf<BlockSideEffect>()
        val job = launch {
            viewModel.container.sideEffectFlow.collect { sideEffects += it }
        }

        viewModel.trackBlockShown(
            packageName = "com.instagram.android",
            blockSource = AnalyticsBlockSource.MANUAL_KEEP,
            routineId = null,
        )
        delay(100)
        val suggestion = viewModel.container.stateFlow.value.repeatBlockRoutineSuggestion
        assertNotNull(suggestion)

        viewModel.openRepeatBlockRoutineSuggestion()
        delay(50)
        job.cancel()

        assertEquals(
            listOf(
                "repeat_block_shown" to RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                "repeat_block_clicked" to RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
            ),
            analytics.repeatBlockEvents.map { it.name to it.surface },
        )
        assertEquals(
            listOf(BlockSideEffect.NavigateRoutineWithRepeatBlockPrefill(suggestion!!)),
            sideEffects,
        )
    }

    // Polls until [predicate] holds instead of relying on a fixed delay, so async analytics
    // dispatches settle deterministically even under CI load (fixes flaky delay(50) races).
    private suspend fun awaitUntil(
        timeoutMillis: Long = 2_000,
        predicate: () -> Boolean,
    ) {
        withTimeout(timeoutMillis) {
            while (!predicate()) {
                delay(10)
            }
        }
    }

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: BlockRecordingKeepAnalytics,
        lockHistoryRepository: LockHistoryRepository = LockHistoryRepository(LockHistoryDaoWithSessions(emptyList())),
        routineRepository: RoutineRepository = EmptyRoutineRepository(),
        repeatBlockSuggestionStore: RepeatBlockRoutineSuggestionStore = RepeatBlockRoutineSuggestionStore(dataStore),
    ): BlockViewModel =
        BlockViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            blockAnalyticsCoordinator = ordinaryBlockAnalyticsCoordinator(dataStore, analytics),
            emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
                settingsStore = EmergencyUnlockSettingsStore(dataStore),
                blockingStateStore = BlockingStateStore(dataStore),
                repository = EmergencyUnlockRepository(FakeEmergencyUnlockDao()),
                analytics = analytics,
            ),
            lockHistoryRepository = lockHistoryRepository,
            routineRepository = routineRepository,
            repeatBlockSuggestionStore = repeatBlockSuggestionStore,
            appCategoryResolver = AppCategoryResolver.FromPackageName,
        )

    private fun ordinaryBlockAnalyticsCoordinator(
        dataStore: FakeDataStore,
        analytics: KeepAnalytics,
    ): BlockAnalyticsCoordinator {
        val blockingStore = BlockingStateStore(dataStore)
        val marker = object : FirstCoreActionMarker {
            override suspend fun read(nowMillis: Long): FirstCoreActionMarkerState {
                val state = blockingStore.readFirstCoreActionState(nowMillis)
                return FirstCoreActionMarkerState(
                    state.firstOpenTimestampMillis,
                    state.hasTrackedFirstCoreAction,
                )
            }

            override suspend fun mark(firstOpenTimestampMillis: Long) {
                blockingStore.markFirstCoreActionTracked(firstOpenTimestampMillis)
            }
        }
        return BlockAnalyticsCoordinator(
            attributionStore = NoAttributionStore,
            activePracticeAt = { null },
            firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
                reservationStore = FirstCoreActionReservationStore { false },
                marker = marker,
            ),
            outboxDispatcher = NoOpOutboxDispatcher,
            directDelivery = KeepBlockDirectAnalyticsDelivery(analytics),
            nowMillis = System::currentTimeMillis,
        )
    }

    private fun lockHistoryAt(dateTime: LocalDateTime, packageName: String): LockHistoryEntity {
        val start = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return LockHistoryEntity(
            startTimestamp = start,
            endTimestamp = start + 60_000L,
            durationMillis = 60_000L,
            lockedApps = listOf(packageName),
            isRoutine = false,
        )
    }
}

private sealed interface BlockAnalyticsCall {
    data class AppBlockIntercepted(
        val blockSource: String,
        val blockedAppPackage: String,
        val routineId: String?,
        val goalLockId: String? = null,
    ) : BlockAnalyticsCall

    data class FirstCoreActionCompleted(
        val blockingMode: String,
        val blockedAppPackage: String,
        val routineId: String?,
        val goalLockId: String? = null,
    ) : BlockAnalyticsCall

    data class CoreActionCompleted(
        val blockingMode: String,
        val blockedAppPackage: String,
        val routineId: String?,
        val goalLockId: String? = null,
    ) : BlockAnalyticsCall

    data class EmergencyUnlockStepViewed(
        val stepName: String,
        val reasonRequiredEnabled: Boolean,
    ) : BlockAnalyticsCall

    data class EmergencyUnlockValidationBlocked(
        val stepName: String,
        val validationReason: String,
        val reasonRequiredEnabled: Boolean,
    ) : BlockAnalyticsCall

    data class EmergencyUnlockCancelled(
        val stepName: String,
        val reasonRequiredEnabled: Boolean,
    ) : BlockAnalyticsCall

    data class ParentModeBlockIntercepted(
        val blockContext: String,
    ) : BlockAnalyticsCall
}

private data class RepeatBlockAnalyticsCall(
    val name: String,
    val surface: String,
    val payload: RepeatBlockRoutineSuggestionAnalyticsPayload,
)

private class BlockRecordingKeepAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val calls = mutableListOf<BlockAnalyticsCall>()
    val repeatBlockEvents = mutableListOf<RepeatBlockAnalyticsCall>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }

    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackEmergencyUnlockStepViewed(
        stepName: String,
        reasonRequiredEnabled: Boolean,
        source: String,
    ) {
        calls += BlockAnalyticsCall.EmergencyUnlockStepViewed(stepName, reasonRequiredEnabled)
    }

    override fun trackEmergencyUnlockValidationBlocked(
        stepName: String,
        validationReason: String,
        reasonRequiredEnabled: Boolean,
        source: String,
    ) {
        calls += BlockAnalyticsCall.EmergencyUnlockValidationBlocked(
            stepName = stepName,
            validationReason = validationReason,
            reasonRequiredEnabled = reasonRequiredEnabled,
        )
    }

    override fun trackEmergencyUnlockCancelled(
        stepName: String,
        reasonRequiredEnabled: Boolean,
        source: String,
        cancelSource: String,
    ) {
        calls += BlockAnalyticsCall.EmergencyUnlockCancelled(stepName, reasonRequiredEnabled)
    }

    override fun trackAppBlockIntercepted(
        blockSource: String,
        blockedAppPackage: String,
        routineId: String?,
        goalLockId: String?,
        promiseOrigin: FirstPromiseOrigin?,
    ) {
        calls += BlockAnalyticsCall.AppBlockIntercepted(
            blockSource = blockSource,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
            goalLockId = goalLockId,
        )
    }

    override fun trackParentModeBlockIntercepted(blockContext: String) {
        calls += BlockAnalyticsCall.ParentModeBlockIntercepted(blockContext)
    }

    override fun trackFirstCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String?,
        goalLockId: String?,
    ) {
        calls += BlockAnalyticsCall.FirstCoreActionCompleted(
            blockingMode = blockingMode,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
            goalLockId = goalLockId,
        )
    }

    override fun trackCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String?,
        goalLockId: String?,
    ) {
        calls += BlockAnalyticsCall.CoreActionCompleted(
            blockingMode = blockingMode,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
            goalLockId = goalLockId,
        )
    }

    override fun trackRepeatBlockRoutineSuggestionShown(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) {
        repeatBlockEvents += RepeatBlockAnalyticsCall("repeat_block_shown", surface, suggestion)
    }

    override fun trackRepeatBlockRoutineSuggestionDismissed(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) {
        repeatBlockEvents += RepeatBlockAnalyticsCall("repeat_block_dismissed", surface, suggestion)
    }

    override fun trackRepeatBlockRoutineSuggestionClicked(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) {
        repeatBlockEvents += RepeatBlockAnalyticsCall("repeat_block_clicked", surface, suggestion)
    }
}

private open class LockHistoryDaoWithSessions(
    private val sessions: List<LockHistoryEntity>,
) : LockHistoryDao {
    override suspend fun insert(entity: LockHistoryEntity) = Unit

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> =
        flowOf(sessions.filter { it.startTimestamp >= startMillis && it.startTimestamp < endMillis })

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = flowOf(sessions)

    override suspend fun countSuccessfulSessions(): Int = sessions.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        sessions.count { it.startTimestamp >= timestampMillis }
}

private class EmptyRoutineRepository : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> = flowOf(emptyList())
}
