package com.uiery.keep.feature.onboarding.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.AnalyticsOutcome
import com.uiery.keep.analytics.AnalyticsPermissionName
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class NotificationSettingViewModel internal constructor(
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
        analytics,
        null,
        kotlinx.coroutines.Dispatchers.Unconfined,
        kotlinx.coroutines.Dispatchers.Unconfined,
    )
    private val transitionMutex = Mutex()
    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_NOTIFICATION)
        analytics.trackOnboardingStepView(OnboardingStepName.NOTIFICATION)
    }

    fun onPermissionDenied() {
        trackNotificationPermissionDenied()
    }

    fun onPermissionDeniedAndContinue() {
        trackNotificationPermissionDenied()
        analytics.trackOnboardingStepComplete(OnboardingStepName.NOTIFICATION)
    }

    private fun trackNotificationPermissionDenied() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.DENIED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
    }

    fun onPermissionGranted() {
        analytics.trackPermissionOutcome(
            permissionName = AnalyticsPermissionName.NOTIFICATIONS,
            outcome = AnalyticsOutcome.GRANTED,
            stepName = OnboardingStepName.NOTIFICATION,
        )
        analytics.trackOnboardingStepComplete(OnboardingStepName.NOTIFICATION)
    }

    fun onFirstPromisePermissionResult(
        granted: Boolean,
        onNavigatePersistence: () -> Unit,
    ) {
        viewModelScope.launch(dispatcher) {
            transitionMutex.withLock {
                val store = draftStore ?: return@withLock
                if (store.beginPersistence() is FirstPromiseStateMutation.Changed) {
                    withContext(mainDispatcher) {
                        if (granted) onPermissionGranted() else onPermissionDeniedAndContinue()
                        onNavigatePersistence()
                    }
                }
            }
        }
    }
}
