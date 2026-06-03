package com.uiery.keep

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.service.EmergencyUnlockCoordinator
import kotlinx.coroutines.delay
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
                emergencyUnlockDao = FakeEmergencyUnlockDao(),
                analytics = analytics,
            ),
        )
}

private sealed interface BlockAnalyticsCall {
    data class AppBlockIntercepted(
        val blockSource: String,
        val blockedAppPackage: String,
        val routineId: String?,
    ) : BlockAnalyticsCall

    data class FirstCoreActionCompleted(
        val blockingMode: String,
        val blockedAppPackage: String,
        val routineId: String?,
    ) : BlockAnalyticsCall

    data class CoreActionCompleted(
        val blockingMode: String,
        val blockedAppPackage: String,
        val routineId: String?,
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

    override fun trackAppBlockIntercepted(
        blockSource: String,
        blockedAppPackage: String,
        routineId: String?,
    ) {
        calls += BlockAnalyticsCall.AppBlockIntercepted(
            blockSource = blockSource,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
        )
    }

    override fun trackFirstCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String?,
    ) {
        calls += BlockAnalyticsCall.FirstCoreActionCompleted(
            blockingMode = blockingMode,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
        )
    }

    override fun trackCoreActionCompleted(
        elapsedSinceFirstOpenSeconds: Long,
        blockingMode: String,
        blockedAppPackage: String,
        routineId: String?,
    ) {
        calls += BlockAnalyticsCall.CoreActionCompleted(
            blockingMode = blockingMode,
            blockedAppPackage = blockedAppPackage,
            routineId = routineId,
        )
    }
}
