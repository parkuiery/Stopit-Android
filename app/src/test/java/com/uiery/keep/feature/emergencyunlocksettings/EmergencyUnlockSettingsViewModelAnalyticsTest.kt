package com.uiery.keep.feature.emergencyunlocksettings

import com.uiery.keep.analytics.AnalyticsEmergencyUnlockDurationCountBucket
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockManualResetResult
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockRefillMode
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockRemainingUnlocksBucket
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockSettingName
import com.uiery.keep.analytics.AnalyticsEmergencyUnlockSettingsValueBucket
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.EmergencyUnlockDao
import com.uiery.keep.database.entity.EmergencyUnlockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.EmergencyUnlockSettingsStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.service.EmergencyUnlockCoordinator
import com.uiery.keep.service.EmergencyUnlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyUnlockSettingsViewModelAnalyticsTest {
    @Test
    fun initLogsEmergencyUnlockSettingsScreenView() {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()

        createViewModel(analytics = analytics)

        assertEquals(listOf(KeepAnalyticsScreen.EMERGENCY_UNLOCK_SETTINGS), analytics.screenViews)
    }

    @Test
    fun settingChangesTrackPrivacySafeBucketsFromMenuSurface() = runBlocking {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()
        val viewModel = createViewModel(analytics = analytics)

        viewModel.applyEnabled(false)
        viewModel.applyDailyLimit(4)
        viewModel.applyDurationToggle(15)
        viewModel.applyReasonRequired(false)
        viewModel.applyAutoResetEnabled(EmergencyUnlockRefillMode.Manual.autoResetEnabled)

        assertEquals(
            listOf(
                SettingsChangedCall(
                    settingName = AnalyticsEmergencyUnlockSettingName.ENABLED,
                    valueBucket = AnalyticsEmergencyUnlockSettingsValueBucket.OFF,
                    refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                    durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                    source = AnalyticsSource.MENU,
                ),
                SettingsChangedCall(
                    settingName = AnalyticsEmergencyUnlockSettingName.DAILY_LIMIT,
                    valueBucket = AnalyticsEmergencyUnlockSettingsValueBucket.FOUR_PLUS,
                    refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                    durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                    source = AnalyticsSource.MENU,
                ),
                SettingsChangedCall(
                    settingName = AnalyticsEmergencyUnlockSettingName.DURATION_OPTIONS,
                    valueBucket = AnalyticsEmergencyUnlockSettingsValueBucket.LONG_INCLUDED,
                    refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                    durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.FOUR_PLUS,
                    source = AnalyticsSource.MENU,
                ),
                SettingsChangedCall(
                    settingName = AnalyticsEmergencyUnlockSettingName.REASON_REQUIRED,
                    valueBucket = AnalyticsEmergencyUnlockSettingsValueBucket.OFF,
                    refillMode = AnalyticsEmergencyUnlockRefillMode.NOT_APPLICABLE,
                    durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                    source = AnalyticsSource.MENU,
                ),
                SettingsChangedCall(
                    settingName = AnalyticsEmergencyUnlockSettingName.REFILL_MODE,
                    valueBucket = AnalyticsEmergencyUnlockSettingsValueBucket.MANUAL,
                    refillMode = AnalyticsEmergencyUnlockRefillMode.MANUAL,
                    durationCountBucket = AnalyticsEmergencyUnlockDurationCountBucket.NOT_APPLICABLE,
                    source = AnalyticsSource.MENU,
                ),
            ),
            analytics.settingsChangedCalls,
        )
    }

    @Test
    fun unchangedSettingsDoNotTrackSettingChange() = runBlocking {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()
        val viewModel = createViewModel(analytics = analytics)

        viewModel.applyEnabled(true)
        viewModel.applyDailyLimit(3)
        viewModel.applyReasonRequired(true)
        viewModel.applyAutoResetEnabled(EmergencyUnlockRefillMode.Daily.autoResetEnabled)

        assertEquals(emptyList<SettingsChangedCall>(), analytics.settingsChangedCalls)
    }

    @Test
    fun durationToggleThatKeepsOnlyAllowedOptionDoesNotTrackSettingChange() = runBlocking {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("3")
        }
        val viewModel = createViewModel(dataStore = dataStore, analytics = analytics)

        viewModel.applyDurationToggle(3)

        assertEquals(emptyList<SettingsChangedCall>(), analytics.settingsChangedCalls)
    }

    @Test
    fun invalidDurationDoesNotTrackSettingChange() = runBlocking {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()
        val viewModel = createViewModel(analytics = analytics)

        viewModel.applyDurationToggle(999)

        assertEquals(emptyList<SettingsChangedCall>(), analytics.settingsChangedCalls)
    }

    @Test
    fun manualResetTracksRemainingBucketWithoutRawTimestamp() = runBlocking {
        val analytics = RecordingEmergencyUnlockSettingsAnalytics()
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.EMERGENCY_UNLOCK_AUTO_RESET_ENABLED] = false
            this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 3
        }
        val viewModel = createViewModel(
            dataStore = dataStore,
            dao = RecordingEmergencyUnlockSettingsDao(countSinceResult = 3),
            analytics = analytics,
        )

        viewModel.applyManualReset()

        assertEquals(
            listOf(
                ManualResetCall(
                    remainingUnlocksBucket = AnalyticsEmergencyUnlockRemainingUnlocksBucket.ZERO,
                    source = AnalyticsSource.MENU,
                    resetResult = AnalyticsEmergencyUnlockManualResetResult.COMPLETED,
                ),
            ),
            analytics.manualResetCalls,
        )
    }
}

private fun createViewModel(
    dataStore: FakeDataStore = FakeDataStore(),
    dao: RecordingEmergencyUnlockSettingsDao = RecordingEmergencyUnlockSettingsDao(),
    analytics: RecordingEmergencyUnlockSettingsAnalytics = RecordingEmergencyUnlockSettingsAnalytics(),
): EmergencyUnlockSettingsViewModel =
    EmergencyUnlockSettingsViewModel(
        settingsStore = EmergencyUnlockSettingsStore(dataStore),
        emergencyUnlockCoordinator = EmergencyUnlockCoordinator(
            settingsStore = EmergencyUnlockSettingsStore(dataStore),
            blockingStateStore = BlockingStateStore(dataStore),
            repository = EmergencyUnlockRepository(dao),
            analytics = analytics,
        ),
        analytics = analytics,
    )

private class RecordingEmergencyUnlockSettingsDao(
    private val countTodayResult: Int = 0,
    private val countSinceResult: Int = 0,
) : EmergencyUnlockDao {
    override suspend fun insert(entity: EmergencyUnlockEntity): Long = 1L
    override suspend fun deleteById(id: Long) = Unit

    override fun fetchByDateRange(start: Long, end: Long): Flow<List<EmergencyUnlockEntity>> = emptyFlow()

    override suspend fun countToday(todayStart: Long): Int = countTodayResult

    override suspend fun countSince(timestampMillis: Long): Int = countSinceResult
}

private data class SettingsChangedCall(
    val settingName: String,
    val valueBucket: String,
    val refillMode: String,
    val durationCountBucket: String,
    val source: String,
)

private data class ManualResetCall(
    val remainingUnlocksBucket: String,
    val source: String,
    val resetResult: String?,
)

private class RecordingEmergencyUnlockSettingsAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val settingsChangedCalls = mutableListOf<SettingsChangedCall>()
    val manualResetCalls = mutableListOf<ManualResetCall>()

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

    override fun trackEmergencyUnlockSettingsChanged(
        settingName: String,
        valueBucket: String,
        refillMode: String,
        durationCountBucket: String,
        source: String,
    ) {
        settingsChangedCalls += SettingsChangedCall(
            settingName = settingName,
            valueBucket = valueBucket,
            refillMode = refillMode,
            durationCountBucket = durationCountBucket,
            source = source,
        )
    }

    override fun trackEmergencyUnlockManualResetRequested(
        remainingUnlocksBucket: String,
        source: String,
        resetResult: String?,
    ) {
        manualResetCalls += ManualResetCall(
            remainingUnlocksBucket = remainingUnlocksBucket,
            source = source,
            resetResult = resetResult,
        )
    }
}
