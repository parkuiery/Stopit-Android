package com.uiery.keep.feature.onboarding.usageaccess

import android.content.ActivityNotFoundException
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionAttempt
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.onboarding.firstPromiseStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAccessViewModelTest {
    @Test
    fun settingsOpenedIsRecordedOnlyAfterLaunchSuccess() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
        val viewModel = UsageAccessViewModel(analytics, store, { false }, Dispatchers.Unconfined)
        viewModel.openSettings { UsageSettingsLaunchResult.Opened }; delay(20)
        assertEquals(PendingSystemAction.UsageAccess, store.readState().pendingSystemAction)
        assertEquals(1, analytics.permissions(AnalyticsOutcome.SETTINGS_OPENED).size)
    }

    @Test
    fun launcherFailuresCloseOnlyTheirAttemptAsUnknown() = runBlocking {
        listOf<() -> UsageSettingsLaunchResult>(
            { throw ActivityNotFoundException() },
            { throw SecurityException() },
            { UsageSettingsLaunchResult.Unavailable },
        ).forEach { launcher ->
            val analytics = FirstPromiseRecordingAnalytics()
            val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
            UsageAccessViewModel(analytics, store, { false }, Dispatchers.Unconfined).openSettings(launcher)
            delay(20)
            assertEquals(UsagePermissionOutcome.Unknown, store.readState().usagePermissionAttempt?.terminalOutcome)
            assertEquals(1, analytics.permissions(AnalyticsOutcome.UNKNOWN).size)
            assertTrue(analytics.permissions(AnalyticsOutcome.SETTINGS_OPENED).isEmpty())
        }
    }

    @Test
    fun sameProcessDeniedCanRetryAndLaterBecomeGranted() = runBlocking {
        var granted = false
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
        val viewModel = UsageAccessViewModel(analytics, store, { granted }, Dispatchers.Unconfined)
        viewModel.openSettings { UsageSettingsLaunchResult.Opened }; delay(20)
        viewModel.onResume(); delay(20)
        assertEquals(UsagePermissionOutcome.Denied, store.readState().usagePermissionAttempt?.terminalOutcome)
        viewModel.openSettings { UsageSettingsLaunchResult.Opened }; delay(20)
        granted = true
        viewModel.onResume(); delay(20)
        assertEquals(UsagePermissionOutcome.Granted, store.readState().usagePermissionAttempt?.terminalOutcome)
        assertEquals(listOf(AnalyticsOutcome.DENIED, AnalyticsOutcome.GRANTED), analytics.terminalOutcomes())
    }

    @Test
    fun recreationWithFalsePermissionIsUnresolvedAndDoesNotGuessOutcome() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
        UsageAccessViewModel(analytics, store, { false }, Dispatchers.Unconfined).openSettings { UsageSettingsLaunchResult.Opened }
        delay(20)
        val recreated = UsageAccessViewModel(analytics, store, { false }, Dispatchers.Unconfined)
        recreated.reconcileAfterRecreation()
        delay(20)
        assertEquals(UsagePermissionLaunchState.UnresolvedAfterRecreation, store.readState().usagePermissionAttempt?.launchState)
        assertNull(store.readState().usagePermissionAttempt?.terminalOutcome)
        assertTrue(recreated.container.stateFlow.value.settingsUnavailable)
        assertTrue(analytics.terminalOutcomes().isEmpty())
    }

    @Test
    fun manualAttemptRecordsSkippedWithoutLeakingAttemptIdAndCompletesOnce() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val viewModel = UsageAccessViewModel(analytics, firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Study), { false }, Dispatchers.Unconfined)
        viewModel.onStepViewed(); viewModel.onStepViewed()
        viewModel.chooseManual(); viewModel.chooseManual(); delay(30)
        assertEquals(1, analytics.permissions(AnalyticsOutcome.SKIPPED).size)
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepView(OnboardingStepName.USAGE_ACCESS) })
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.USAGE_ACCESS) })
        assertTrue(analytics.calls.none { it.toString().contains("attempt", ignoreCase = true) })
    }

    @Test
    fun recreatedGrantedAttemptDurablyCompletesAndResumesNavigationExactlyOnce() = runBlocking {
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(
            FirstPromiseOnboardingState(
                assignment = com.uiery.keep.domain.firstpromise.OnboardingVariant.PromiseCoachV1,
                assignmentVersion = com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion.V1,
                phase = FirstPromisePhase.UsageAccessPending,
                goal = FirstPromiseGoal.Focus,
                usagePermissionAttempt = UsagePermissionAttempt(
                    id = 8L,
                    launchState = UsagePermissionLaunchState.Opened,
                    terminalOutcome = UsagePermissionOutcome.Granted,
                ),
            ),
        )
        val viewModel = UsageAccessViewModel(analytics, store, { true }, Dispatchers.Unconfined)
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.reconcileAfterRecreation()
        viewModel.reconcileAfterRecreation()

        assertEquals(
            UsageAccessSideEffect.NavigateUsageAnalysis,
            withTimeout(1_000L) { navigation.await() },
        )
        assertEquals(FirstPromisePhase.Analyzing, store.readState().phase)
        assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.USAGE_ACCESS) })
        assertTrue(analytics.permissions(AnalyticsOutcome.GRANTED).isEmpty())
    }

    @Test
    fun settingsAndManualCrossTapsCommitOnlyTheFirstChoice() = runBlocking {
        listOf(true, false).forEach { settingsFirst ->
            val analytics = FirstPromiseRecordingAnalytics()
            val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
            val viewModel = UsageAccessViewModel(analytics, store, { true }, Dispatchers.Unconfined)
            val navigation = async { viewModel.container.sideEffectFlow.first() }

            if (settingsFirst) {
                viewModel.openSettings { UsageSettingsLaunchResult.Opened }
                viewModel.chooseManual()
                viewModel.onResume()
            } else {
                viewModel.chooseManual()
                viewModel.openSettings { UsageSettingsLaunchResult.Opened }
            }

            assertEquals(
                if (settingsFirst) UsageAccessSideEffect.NavigateUsageAnalysis else UsageAccessSideEffect.NavigateManualAppSelect,
                withTimeout(1_000) { navigation.await() },
            )
            assertEquals(
                if (settingsFirst) FirstPromisePhase.Analyzing else FirstPromisePhase.ManualSelectPending,
                store.readState().phase,
            )
            assertEquals(1, analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.USAGE_ACCESS) })
        }
    }

    @Test
    fun unavailableSettingsCanBeRetried() = runBlocking {
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
        val viewModel = UsageAccessViewModel(FirstPromiseRecordingAnalytics(), store, { false }, Dispatchers.Unconfined)

        viewModel.openSettings { UsageSettingsLaunchResult.Unavailable }
        delay(10)
        viewModel.openSettings { UsageSettingsLaunchResult.Opened }
        delay(10)

        assertEquals(2L, store.readState().usagePermissionAttempt?.id)
        assertEquals(UsagePermissionLaunchState.Opened, store.readState().usagePermissionAttempt?.launchState)
        assertEquals(PendingSystemAction.UsageAccess, store.readState().pendingSystemAction)
    }

    @Test
    fun pendingNotLaunchedCrashWindowReconcilesPermissionWithoutClearingEvidenceEarly() = runBlocking {
        listOf(true, false).forEach { granted ->
            val state = FirstPromiseOnboardingState(
                assignment = com.uiery.keep.domain.firstpromise.OnboardingVariant.PromiseCoachV1,
                assignmentVersion = com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion.V1,
                phase = FirstPromisePhase.UsageAccessPending,
                goal = FirstPromiseGoal.Focus,
                pendingSystemAction = PendingSystemAction.UsageAccess,
                usagePermissionAttempt = UsagePermissionAttempt(7L, UsagePermissionLaunchState.NotLaunched),
            )
            val store = firstPromiseStore(state)
            val viewModel = UsageAccessViewModel(FirstPromiseRecordingAnalytics(), store, { granted }, Dispatchers.Unconfined)

            viewModel.reconcileAfterRecreation()
            delay(10)

            val reconciled = store.readState()
            assertNull(reconciled.pendingSystemAction)
            if (granted) {
                assertEquals(UsagePermissionOutcome.Granted, reconciled.usagePermissionAttempt?.terminalOutcome)
                assertEquals(FirstPromisePhase.Analyzing, reconciled.phase)
            } else {
                assertEquals(UsagePermissionLaunchState.UnresolvedAfterRecreation, reconciled.usagePermissionAttempt?.launchState)
                assertNull(reconciled.usagePermissionAttempt?.terminalOutcome)
                assertEquals(FirstPromisePhase.UsageAccessPending, reconciled.phase)
                assertTrue(viewModel.container.stateFlow.value.settingsUnavailable)
            }
        }
    }
}

private fun FirstPromiseRecordingAnalytics.permissions(outcome: String) = calls.filterIsInstance<FirstPromiseAnalyticsCall.Permission>().filter {
    it.name == AnalyticsPermissionName.USAGE_ACCESS && it.outcome == outcome && it.step == OnboardingStepName.USAGE_ACCESS
}

private fun FirstPromiseRecordingAnalytics.terminalOutcomes() = calls.filterIsInstance<FirstPromiseAnalyticsCall.Permission>()
    .map { it.outcome }.filter { it != AnalyticsOutcome.SETTINGS_OPENED }
