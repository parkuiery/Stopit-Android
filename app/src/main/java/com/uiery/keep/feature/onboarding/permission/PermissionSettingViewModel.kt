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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PermissionSettingViewModel internal constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore?,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
    ) : this(analytics, draftStore, kotlinx.coroutines.Dispatchers.IO, kotlinx.coroutines.Dispatchers.Main.immediate)

    internal constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore?,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : this(analytics, draftStore, dispatcher, dispatcher)

    internal constructor(analytics: KeepAnalytics) : this(
        analytics = analytics,
        draftStore = null,
        dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        mainDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
    )
    private val transitionMutex = Mutex()
    private val controlNavigationPosted = AtomicBoolean(false)
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

    fun onControlPermissionGranted(onNavigateNotification: () -> Unit) {
        if (!controlNavigationPosted.compareAndSet(false, true)) return
        onPermissionGranted()
        onNavigateNotification()
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
            withContext(mainDispatcher) { onLoaded(draft.startMinutes) }
            if (accessibilityGranted && state.phase == FirstPromisePhase.AccessibilityPending) {
                continueFirstPromise(trackGrant = false, onNavigateNotification)
            }
        }
    }

    fun onFirstPromisePermissionGranted(onNavigateNotification: () -> Unit) {
        viewModelScope.launch(dispatcher) {
            continueFirstPromise(trackGrant = true, onNavigateNotification)
        }
    }

    fun onFirstPromiseBack(onNavigateProposal: () -> Unit) {
        viewModelScope.launch(dispatcher) {
            transitionMutex.withLock {
                val store = draftStore ?: return@withLock
                if (store.returnToDraft() is FirstPromiseStateMutation.Changed) {
                    withContext(mainDispatcher) { onNavigateProposal() }
                }
            }
        }
    }

    private suspend fun continueFirstPromise(
        trackGrant: Boolean,
        onNavigateNotification: () -> Unit,
    ) {
        transitionMutex.withLock {
            val store = draftStore ?: return@withLock
            if (store.requestNotification() is FirstPromiseStateMutation.Changed) {
                withContext(mainDispatcher) {
                    if (trackGrant) onPermissionGranted()
                    onNavigateNotification()
                }
            }
        }
    }
}
