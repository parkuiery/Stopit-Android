package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.database.entity.LockHistoryEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.ReviewPromptStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.uiery.keep.feature.review.FakeAccessibilityChecker
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.feature.review.FakeEmergencyUnlockDao
import com.uiery.keep.feature.review.FakeLockHistoryDao
import com.uiery.keep.feature.review.FakeReviewLauncher
import com.uiery.keep.feature.review.FakeReviewRemoteConfig
import com.uiery.keep.feature.review.InAppReviewManager
import com.uiery.keep.feature.review.ReviewBuildConfig
import com.uiery.keep.feature.review.ReviewEligibilityEvaluator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    ): HomeViewModel {
        val reviewPromptStateStore = ReviewPromptStateStore(dataStore)
        return HomeViewModel(
            dataStore = dataStore,
            blockingStateStore = BlockingStateStore(dataStore),
            reviewPromptStateStore = reviewPromptStateStore,
            analytics = analytics,
            lockHistoryDao = lockHistoryDao,
            reviewEligibility = ReviewEligibilityEvaluator(
                blockingStateStore = BlockingStateStore(dataStore),
                reviewPromptStateStore = reviewPromptStateStore,
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
                reviewPromptStateStore = reviewPromptStateStore,
                clock = clock,
            ),
        )
    }
}

private class HomeRecordingLockHistoryDao : LockHistoryDao {
    val inserted = mutableListOf<LockHistoryEntity>()

    override suspend fun insert(entity: LockHistoryEntity) {
        inserted += entity
    }

    override fun fetchByDateRange(startMillis: Long, endMillis: Long): Flow<List<LockHistoryEntity>> = emptyFlow()

    override fun fetchAll(): Flow<List<LockHistoryEntity>> = emptyFlow()

    override suspend fun countSuccessfulSessions(): Int = inserted.size

    override suspend fun countSuccessfulSessionsSince(timestampMillis: Long): Int =
        inserted.count { it.startTimestamp >= timestampMillis }
}

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
}
