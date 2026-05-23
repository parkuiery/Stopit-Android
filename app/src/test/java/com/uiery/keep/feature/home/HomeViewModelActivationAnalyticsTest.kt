package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsEndReason
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSource
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
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

        assertEquals(HomeAnalyticsCall.FirstLockConfigured(AnalyticsSource.HOME_TIMER, 1), analytics.calls[0])
        assertEquals(HomeAnalyticsCall.LockScheduled(AnalyticsScheduleType.TIMER), analytics.calls[1])
        assertEquals(
            HomeAnalyticsCall.LockSessionStarted(
                source = AnalyticsSource.HOME_TIMER,
                isRoutine = false,
            ),
            analytics.calls[2],
        )
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    private fun createViewModel(
        dataStore: FakeDataStore,
        analytics: HomeRecordingKeepAnalytics,
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
