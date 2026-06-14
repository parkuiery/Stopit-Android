package com.uiery.keep

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.PreferencesKey
import java.time.Instant
import java.time.ZoneId
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

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
        delay(50)

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

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: BlockRecordingKeepAnalytics,
    ): BlockViewModel =
        BlockViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
            emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
                settingsStore = EmergencyUnlockSettingsStore(dataStore),
                blockingStateStore = BlockingStateStore(dataStore),
                repository = EmergencyUnlockRepository(FakeEmergencyUnlockDao()),
                analytics = analytics,
            ),
        )
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
}

private class BlockRecordingKeepAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val calls = mutableListOf<BlockAnalyticsCall>()

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
    ) {
        calls += BlockAnalyticsCall.AppBlockIntercepted(
            blockSource = blockSource,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
            goalLockId = goalLockId,
        )
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
}
