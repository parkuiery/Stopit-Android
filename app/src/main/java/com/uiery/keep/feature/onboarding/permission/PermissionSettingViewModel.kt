package com.uiery.keep.feature.onboarding.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PermissionSettingViewModel internal constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore?,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
    ) : this(analytics, draftStore, kotlinx.coroutines.Dispatchers.IO)

    internal constructor(analytics: KeepAnalytics) : this(
        analytics = analytics,
        draftStore = null,
        dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )
    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_PERMISSION)
        analytics.trackOnboardingStepView(OnboardingStepName.PERMISSION)
    }

    fun onPermissionGranted() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.ACCESSIBILITY,
            outcome = AnalyticsOutcome.GRANTED,
            stepName = OnboardingStepName.PERMISSION,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.PERMISSION)
    }

    fun onPermissionSettingsOpened() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.ACCESSIBILITY,
            outcome = AnalyticsOutcome.SETTINGS_OPENED,
            stepName = OnboardingStepName.PERMISSION,
        )
    }

    fun loadFirstPromise(
        accessibilityGranted: Boolean,
        onLoaded: (startMinutes: Int) -> Unit,
        onNavigateNotification: () -> Unit,
    ) {
        viewModelScope.launch(dispatcher) {
            val store = draftStore ?: return@launch
            val state = runCatching { store.readState() }.getOrNull() ?: return@launch
            val draft = state.draft ?: return@launch
            onLoaded(draft.startMinutes)
            if (accessibilityGranted && state.phase == FirstPromisePhase.AccessibilityPending) {
                continueFirstPromise(onNavigateNotification)
            }
        }
    }

    fun onFirstPromisePermissionGranted(onNavigateNotification: () -> Unit) {
        onPermissionGranted()
        viewModelScope.launch(dispatcher) { continueFirstPromise(onNavigateNotification) }
    }

    fun onFirstPromiseBack(onNavigateProposal: () -> Unit) {
        viewModelScope.launch(dispatcher) {
            val store = draftStore ?: return@launch
            when (store.returnToDraft()) {
                is FirstPromiseStateMutation.Changed -> onNavigateProposal()
                FirstPromiseStateMutation.NoOp -> {
                    if (store.readState().phase == FirstPromisePhase.DraftReady) onNavigateProposal()
                }
                FirstPromiseStateMutation.Rejected -> Unit
            }
        }
    }

    private suspend fun continueFirstPromise(onNavigateNotification: () -> Unit) {
        val store = draftStore ?: return
        when (store.requestNotification()) {
            is FirstPromiseStateMutation.Changed -> onNavigateNotification()
            FirstPromiseStateMutation.NoOp -> {
                if (store.readState().phase == FirstPromisePhase.NotificationPending) {
                    onNavigateNotification()
                }
            }
            FirstPromiseStateMutation.Rejected -> Unit
        }
    }
}
