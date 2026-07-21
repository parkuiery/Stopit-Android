package com.uiery.keep.feature.onboarding.usageaccess

import android.content.ActivityNotFoundException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.data.usageinsight.UsageStatsGateway
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import com.uiery.keep.feature.onboarding.usageanalysis.FirstPromiseAnalysisTransientHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

enum class UsageSettingsLaunchResult { Opened, Unavailable }

data class UsageAccessUiState(
    val settingsUnavailable: Boolean = false,
    val isReconciling: Boolean = false,
)

sealed interface UsageAccessSideEffect {
    data object NavigateUsageAnalysis : UsageAccessSideEffect
    data object NavigateManualAppSelect : UsageAccessSideEffect
}

@HiltViewModel
class UsageAccessViewModel internal constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore,
    private val permissionGranted: () -> Boolean,
    private val workDispatcher: CoroutineDispatcher,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher =
        FirstPromiseOnboardingAnalyticsDispatcher(draftStore, analytics),
) : ViewModel(), ContainerHost<UsageAccessUiState, UsageAccessSideEffect> {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
        usageStatsGateway: UsageStatsGateway,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) : this(
        analytics,
        draftStore,
        usageStatsGateway::isPermissionGranted,
        Dispatchers.Main.immediate,
        onboardingAnalyticsDispatcher,
    )

    override val container: Container<UsageAccessUiState, UsageAccessSideEffect> = container(UsageAccessUiState())
    private val viewed = AtomicBoolean(false)
    private val actionInFlight = AtomicBoolean(false)
    private val navigationPosted = AtomicBoolean(false)
    private var openedAttemptInProcess: Long? = null

    fun onStepViewed() {
        if (!viewed.compareAndSet(false, true)) return
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_USAGE_ACCESS)
        viewModelScope.launch(workDispatcher) {
            if (draftStore.markUsageAccessViewed() !is FirstPromiseStateMutation.Rejected) {
                runCatching { onboardingAnalyticsDispatcher.drain() }
            }
        }
    }

    fun openSettings(launcher: () -> UsageSettingsLaunchResult) {
        if (!actionInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(workDispatcher) {
            val attemptId = (draftStore.readState().usagePermissionAttempt?.id ?: 0L) + 1L
            if (!draftStore.beginUsagePermissionSettingsAttempt(attemptId)) {
                actionInFlight.set(false)
                return@launch
            }
            val launchResult = try {
                launcher()
            } catch (_: ActivityNotFoundException) {
                UsageSettingsLaunchResult.Unavailable
            } catch (_: SecurityException) {
                UsageSettingsLaunchResult.Unavailable
            }
            if (launchResult == UsageSettingsLaunchResult.Opened && draftStore.recordUsagePermissionOpened(attemptId)) {
                openedAttemptInProcess = attemptId
                intent { reduce { state.copy(settingsUnavailable = false) } }
                trackPermission(AnalyticsOutcome.SETTINGS_OPENED)
            } else if (draftStore.recordUsagePermissionLaunchFailed(attemptId)) {
                intent { reduce { state.copy(settingsUnavailable = true) } }
                trackPermission(AnalyticsOutcome.UNKNOWN)
                actionInFlight.set(false)
            } else {
                actionInFlight.set(false)
            }
        }
    }

    fun onResume() {
        viewModelScope.launch(workDispatcher) {
            val attemptId = openedAttemptInProcess ?: return@launch
            openedAttemptInProcess = null
            val outcome = draftStore.recordUsagePermissionResume(attemptId, permissionGranted(), sameProcess = true)
            handleTerminalOutcome(outcome, trackNewOutcome = true)
            actionInFlight.set(false)
        }
    }

    fun reconcileAfterRecreation() {
        if (!actionInFlight.compareAndSet(false, true)) return
        val markReconciling = intent { reduce { state.copy(isReconciling = true) } }
        viewModelScope.launch(workDispatcher) {
            try {
                markReconciling.join()
                val outcome = draftStore.reconcileUsagePermissionAfterRecreation(permissionGranted())
                val launchState = draftStore.readState().usagePermissionAttempt?.launchState
                if (
                    launchState == UsagePermissionLaunchState.LaunchFailed ||
                    launchState == UsagePermissionLaunchState.UnresolvedAfterRecreation
                ) {
                    intent { reduce { state.copy(settingsUnavailable = true) } }.join()
                }
                val terminalOutcome = outcome ?: draftStore.readState().usagePermissionAttempt?.terminalOutcome
                handleTerminalOutcome(terminalOutcome, trackNewOutcome = outcome != null)
            } finally {
                try {
                    intent { reduce { state.copy(isReconciling = false) } }.join()
                } finally {
                    actionInFlight.set(false)
                }
            }
        }
    }

    fun chooseManual() {
        if (!actionInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(workDispatcher) {
            val attemptId = (draftStore.readState().usagePermissionAttempt?.id ?: 0L) + 1L
            val mutation = draftStore.chooseManualUsageAccess(attemptId)
            if (mutation is FirstPromiseStateMutation.Changed) {
                trackPermission(AnalyticsOutcome.SKIPPED)
                FirstPromiseAnalysisTransientHolder.clear()
                runCatching { onboardingAnalyticsDispatcher.drain() }
                intent { postSideEffect(UsageAccessSideEffect.NavigateManualAppSelect) }
            } else {
                actionInFlight.set(false)
            }
        }
    }

    private suspend fun handleTerminalOutcome(
        outcome: UsagePermissionOutcome?,
        trackNewOutcome: Boolean,
    ) {
        when (outcome) {
            UsagePermissionOutcome.Granted -> {
                if (trackNewOutcome) trackPermission(AnalyticsOutcome.GRANTED)
                draftStore.completeUsageAccess()
                runCatching { onboardingAnalyticsDispatcher.drain() }
                if (
                    draftStore.readState().phase == com.uiery.keep.domain.firstpromise.FirstPromisePhase.Analyzing &&
                    navigationPosted.compareAndSet(false, true)
                ) {
                    intent { postSideEffect(UsageAccessSideEffect.NavigateUsageAnalysis) }.join()
                }
            }
            UsagePermissionOutcome.Denied -> if (trackNewOutcome) trackPermission(AnalyticsOutcome.DENIED)
            else -> Unit
        }
    }

    private fun trackPermission(outcome: String) = analytics.trackPermissionOutcome(
        permissionName = AnalyticsPermissionName.USAGE_ACCESS,
        outcome = outcome,
        stepName = OnboardingStepName.USAGE_ACCESS,
    )
}
