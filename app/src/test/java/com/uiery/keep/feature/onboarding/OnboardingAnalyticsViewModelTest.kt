package com.uiery.keep.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.onboarding.intro.IntroViewModel
import com.uiery.keep.feature.onboarding.notification.NotificationSettingViewModel
import com.uiery.keep.feature.onboarding.permission.PermissionSettingViewModel
import com.uiery.keep.feature.onboarding.select.SelectAppViewModel
import com.uiery.keep.feature.onboarding.select.canCompleteOnboardingAppSelection
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingAnalyticsViewModelTest {

    private val analytics = RecordingKeepAnalytics()

    @Test
    fun introTracksViewAndCompletion() {
        val viewModel = IntroViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onContinue()

        assertEquals(
            listOf(
                AnalyticsCall.ScreenViewed(KeepAnalyticsScreen.ONBOARDING_INTRO),
                AnalyticsCall.StepViewed(OnboardingStepName.INTRO),
                AnalyticsCall.StepCompleted(OnboardingStepName.INTRO),
            ),
            analytics.calls,
        )
    }

    @Test
    fun controlIntroTracksExperimentExposureOnlyOnThePersistedBooleanTransition() = runBlocking {
        val dataStore = FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                    FirstPromiseOnboardingState(
                        assignment = OnboardingVariant.Control,
                        assignmentVersion = OnboardingAssignmentVersion.V1,
                    ),
                ),
            ),
        )
        val viewModel = IntroViewModel(analytics, FirstPromiseDraftStore(dataStore))

        viewModel.onStepViewed()
        viewModel.onStepViewed()
        delay(100)

        assertEquals(
            listOf(
                AnalyticsCall.ExperimentExposed(
                    variant = OnboardingVariant.Control,
                    assignmentVersion = OnboardingAssignmentVersion.V1,
                ),
            ),
            analytics.calls.filterIsInstance<AnalyticsCall.ExperimentExposed>(),
        )
        assertTrue(FirstPromiseDraftStore(dataStore).readState().pendingOnboardingAnalyticsEvents.isEmpty())
    }

    @Test
    fun notificationTracksRuntimeDeniedOrGrantedAndOnlyCompletesWhenGrantedOrDeniedWithContinue() {
        val viewModel = NotificationSettingViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onPermissionDenied()
        viewModel.onPermissionGranted()

        assertEquals(
            listOf(
                AnalyticsCall.ScreenViewed(KeepAnalyticsScreen.ONBOARDING_NOTIFICATION),
                AnalyticsCall.StepViewed(OnboardingStepName.NOTIFICATION),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.NOTIFICATIONS,
                    outcome = AnalyticsOutcome.DENIED,
                    stepName = OnboardingStepName.NOTIFICATION,
                ),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.NOTIFICATIONS,
                    outcome = AnalyticsOutcome.GRANTED,
                    stepName = OnboardingStepName.NOTIFICATION,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.NOTIFICATION),
            ),
            analytics.calls,
        )
    }

    @Test
    fun notificationPermissionDeniedWithContinueTracksDeniedAndCompletesStep() {
        val viewModel = NotificationSettingViewModel(analytics)

        viewModel.onPermissionDeniedAndContinue()

        assertEquals(
            listOf(
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.NOTIFICATIONS,
                    outcome = AnalyticsOutcome.DENIED,
                    stepName = OnboardingStepName.NOTIFICATION,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.NOTIFICATION),
            ),
            analytics.calls,
        )
    }

    @Test
    fun permissionTracksSettingsOpenAndGrantEvents() {
        val viewModel = PermissionSettingViewModel(analytics)

        viewModel.onStepViewed()
        viewModel.onPermissionSettingsOpened()
        viewModel.onPermissionGranted()

        assertEquals(
            listOf(
                AnalyticsCall.ScreenViewed(KeepAnalyticsScreen.ONBOARDING_PERMISSION),
                AnalyticsCall.StepViewed(OnboardingStepName.PERMISSION),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.ACCESSIBILITY,
                    outcome = AnalyticsOutcome.SETTINGS_OPENED,
                    stepName = OnboardingStepName.PERMISSION,
                ),
                AnalyticsCall.PermissionOutcome(
                    permissionName = AnalyticsPermissionName.ACCESSIBILITY,
                    outcome = AnalyticsOutcome.GRANTED,
                    stepName = OnboardingStepName.PERMISSION,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.PERMISSION),
            ),
            analytics.calls,
        )
    }

    @Test
    fun selectAppTracksScreenViewAndStepView() {
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(FakeDataStore()),
            analytics = analytics,
        )

        viewModel.onStepViewed()

        assertEquals(
            listOf(
                AnalyticsCall.ScreenViewed(KeepAnalyticsScreen.ONBOARDING_SELECT_APP),
                AnalyticsCall.StepViewed(OnboardingStepName.SELECT_APP),
            ),
            analytics.calls,
        )
    }

    @Test
    fun selectAppCompletionWithEmptySelectionDoesNotCompleteOnboardingOrFirstLock() = runBlocking {
        val dataStore = FakeDataStore()
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
        )

        viewModel.selectCategoryComplete(emptySet())
        delay(100)

        assertEquals(emptyList<AnalyticsCall>(), analytics.calls)
        assertEquals(null, dataStore.snapshot()[PreferencesKey.SELECTED_APP_PACKAGES])
        assertEquals(null, dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertEquals(null, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun selectAppCompletionWithSelectionTracksAndStoresOnboardingCompletion() = runBlocking {
        val dataStore = FakeDataStore()
        val viewModel = SelectAppViewModel(
            blockingStateStore = BlockingStateStore(dataStore),
            analytics = analytics,
        )

        viewModel.selectCategoryComplete(setOf("com.example.app"))
        delay(100)

        assertEquals(
            listOf(
                AnalyticsCall.AppSelectionCompleted(
                    selectedAppCount = 1,
                    isOnboarding = true,
                ),
                AnalyticsCall.StepCompleted(OnboardingStepName.SELECT_APP),
                AnalyticsCall.FirstLockConfigured(
                    source = AnalyticsSource.ONBOARDING,
                    selectedAppCount = 1,
                ),
            ),
            analytics.calls,
        )
        assertEquals(setOf("com.example.app"), dataStore.snapshot()[PreferencesKey.SELECTED_APP_PACKAGES])
        assertEquals(false, dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun onboardingAppSelectionRequiresAtLeastOneSelectedPackage() {
        assertFalse(canCompleteOnboardingAppSelection(emptySet()))
        assertTrue(canCompleteOnboardingAppSelection(setOf("com.example.app")))
    }
}

private sealed interface AnalyticsCall {
    data class Event(val name: String, val params: Map<String, Any?>) : AnalyticsCall
    data class ScreenViewed(val screenName: String) : AnalyticsCall
    data class UserPropertySet(val name: String, val value: String) : AnalyticsCall
    data object FirstOpen : AnalyticsCall
    data class StepViewed(val stepName: String) : AnalyticsCall
    data class StepCompleted(val stepName: String) : AnalyticsCall
    data class ExperimentExposed(
        val variant: OnboardingVariant,
        val assignmentVersion: OnboardingAssignmentVersion,
    ) : AnalyticsCall
    data class PermissionOutcome(
        val permissionName: String,
        val outcome: String,
        val stepName: String?,
    ) : AnalyticsCall
    data class AppSelectionCompleted(
        val selectedAppCount: Int,
        val isOnboarding: Boolean,
    ) : AnalyticsCall
    data class FirstLockConfigured(
        val source: String,
        val selectedAppCount: Int?,
    ) : AnalyticsCall
    data class LockSessionStarted(
        val source: String,
        val isRoutine: Boolean?,
    ) : AnalyticsCall
    data class LockSessionEnded(
        val source: String,
        val endReason: String,
        val isRoutine: Boolean?,
    ) : AnalyticsCall
    data class EmergencyUnlockUsed(
        val source: String,
        val unlockCountRemaining: Int?,
    ) : AnalyticsCall
}

private class RecordingKeepAnalytics : KeepAnalytics {
    val calls = mutableListOf<AnalyticsCall>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) {
        calls += AnalyticsCall.Event(name = name, params = params)
    }

    override fun logScreenView(screenName: String) {
        calls += AnalyticsCall.ScreenViewed(screenName = screenName)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        calls += AnalyticsCall.UserPropertySet(name = name, value = value)
    }

    override fun trackFirstOpen() {
        calls += AnalyticsCall.FirstOpen
    }

    override fun trackOnboardingStepView(stepName: String) {
        calls += AnalyticsCall.StepViewed(stepName = stepName)
    }

    override fun trackOnboardingStepComplete(stepName: String) {
        calls += AnalyticsCall.StepCompleted(stepName = stepName)
    }

    override fun trackOnboardingExperimentExposed(
        variant: OnboardingVariant,
        assignmentVersion: OnboardingAssignmentVersion,
    ) {
        calls += AnalyticsCall.ExperimentExposed(
            variant = variant,
            assignmentVersion = assignmentVersion,
        )
    }

    override fun trackPermissionOutcome(
        permissionName: String,
        outcome: String,
        stepName: String?,
    ) {
        calls += AnalyticsCall.PermissionOutcome(
            permissionName = permissionName,
            outcome = outcome,
            stepName = stepName,
        )
    }

    override fun trackAppSelectionCompleted(
        selectedAppCount: Int,
        isOnboarding: Boolean,
    ) {
        calls += AnalyticsCall.AppSelectionCompleted(
            selectedAppCount = selectedAppCount,
            isOnboarding = isOnboarding,
        )
    }

    override fun trackFirstLockConfigured(
        source: String,
        selectedAppCount: Int?,
    ) {
        calls += AnalyticsCall.FirstLockConfigured(
            source = source,
            selectedAppCount = selectedAppCount,
        )
    }

    override fun trackLockSessionStart(
        source: String,
        isRoutine: Boolean?,
    ) {
        calls += AnalyticsCall.LockSessionStarted(
            source = source,
            isRoutine = isRoutine,
        )
    }

    override fun trackLockSessionEnd(
        source: String,
        endReason: String,
        isRoutine: Boolean?,
    ) {
        calls += AnalyticsCall.LockSessionEnded(
            source = source,
            endReason = endReason,
            isRoutine = isRoutine,
        )
    }

    override fun trackEmergencyUnlockUsed(
        source: String,
        unlockCountRemaining: Int?,
    ) {
        calls += AnalyticsCall.EmergencyUnlockUsed(
            source = source,
            unlockCountRemaining = unlockCountRemaining,
        )
    }
}

private object UnusedPreferencesDataStore : DataStore<Preferences> {
    override val data = emptyFlow<Preferences>()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        error("Unused in onboarding analytics tests")
    }
}
