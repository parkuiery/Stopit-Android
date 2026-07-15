package com.uiery.keep.feature.onboarding.usageaccess

import android.content.ActivityNotFoundException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.data.usageinsight.UsageStatsGateway
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.UsagePermissionLaunchState
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
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

data class UsageAccessUiState(val settingsUnavailable: Boolean = false)

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
) : ViewModel(), ContainerHost<UsageAccessUiState, UsageAccessSideEffect> {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
        usageStatsGateway: UsageStatsGateway,
    ) : this(analytics, draftStore, usageStatsGateway::isPermissionGranted, Dispatchers.Main.immediate)

    override val container: Container<UsageAccessUiState, UsageAccessSideEffect> = container(UsageAccessUiState())
    private val viewed = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)
    private val manualChosen = AtomicBoolean(false)
    private val openingSettings = AtomicBoolean(false)
    private var openedAttemptInProcess: Long? = null

    fun onStepViewed() {
        if (!viewed.compareAndSet(false, true)) return
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_USAGE_ACCESS)
        analytics.trackOnboardingStepView(OnboardingStepName.USAGE_ACCESS)
    }

    fun openSettings(launcher: () -> UsageSettingsLaunchResult) {
        if (!openingSettings.compareAndSet(false, true)) return
        viewModelScope.launch(workDispatcher) {
            val attemptId = (draftStore.readState().usagePermissionAttempt?.id ?: 0L) + 1L
            if (!draftStore.beginUsagePermissionAttempt(attemptId)) {
                openingSettings.set(false)
                return@launch
            }
            draftStore.setPendingSystemAction(PendingSystemAction.UsageAccess)
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
                draftStore.clearPendingSystemAction()
                intent { reduce { state.copy(settingsUnavailable = true) } }
                trackPermission(AnalyticsOutcome.UNKNOWN)
                openingSettings.set(false)
            } else {
                openingSettings.set(false)
            }
        }
    }

    fun onResume() {
        viewModelScope.launch(workDispatcher) {
            val attemptId = openedAttemptInProcess ?: return@launch
            openedAttemptInProcess = null
            openingSettings.set(false)
            val outcome = draftStore.recordUsagePermissionResume(attemptId, permissionGranted(), sameProcess = true)
            draftStore.clearPendingSystemAction()
            handleTerminalOutcome(outcome)
        }
    }

    fun reconcileAfterRecreation() {
        viewModelScope.launch(workDispatcher) {
            openingSettings.set(false)
            val outcome = draftStore.reconcileUsagePermissionAfterRecreation(permissionGranted())
            draftStore.clearPendingSystemAction()
            val launchState = draftStore.readState().usagePermissionAttempt?.launchState
            if (
                launchState == UsagePermissionLaunchState.LaunchFailed ||
                launchState == UsagePermissionLaunchState.UnresolvedAfterRecreation
            ) {
                intent { reduce { state.copy(settingsUnavailable = true) } }
            }
            handleTerminalOutcome(outcome)
        }
    }

    fun chooseManual() {
        if (!manualChosen.compareAndSet(false, true)) return
        viewModelScope.launch(workDispatcher) {
            val attemptId = (draftStore.readState().usagePermissionAttempt?.id ?: 0L) + 1L
            if (draftStore.beginManualUsagePermissionAttempt(attemptId)) {
                trackPermission(AnalyticsOutcome.SKIPPED)
            }
            val mutation = draftStore.chooseManualSetup()
            if (mutation is FirstPromiseStateMutation.Changed) {
                completeOnce()
                intent { postSideEffect(UsageAccessSideEffect.NavigateManualAppSelect) }
            }
        }
    }

    private suspend fun handleTerminalOutcome(
        outcome: UsagePermissionOutcome?,
    ) {
        when (outcome) {
            UsagePermissionOutcome.Granted -> {
                trackPermission(AnalyticsOutcome.GRANTED)
                completeOnce()
                intent { postSideEffect(UsageAccessSideEffect.NavigateUsageAnalysis) }
            }
            UsagePermissionOutcome.Denied -> trackPermission(AnalyticsOutcome.DENIED)
            else -> Unit
        }
    }

    private fun trackPermission(outcome: String) = analytics.trackPermissionOutcome(
        permissionName = AnalyticsPermissionName.USAGE_ACCESS,
        outcome = outcome,
        stepName = OnboardingStepName.USAGE_ACCESS,
    )

    private fun completeOnce() {
        if (completed.compareAndSet(false, true)) analytics.trackOnboardingStepComplete(OnboardingStepName.USAGE_ACCESS)
    }
}
