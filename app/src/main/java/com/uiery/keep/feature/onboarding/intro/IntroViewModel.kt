package com.uiery.keep.feature.onboarding.intro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntroViewModel private constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore?,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher?,
    private val exposureDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) : this(
        analytics = analytics,
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        exposureDispatcher = Dispatchers.IO,
    )

    internal constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
    ) : this(
        analytics = analytics,
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = FirstPromiseOnboardingAnalyticsDispatcher(draftStore, analytics),
        exposureDispatcher = Dispatchers.IO,
    )

    internal constructor(analytics: KeepAnalytics) : this(
        analytics = analytics,
        draftStore = null,
        onboardingAnalyticsDispatcher = null,
        exposureDispatcher = Dispatchers.IO,
    )

    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_INTRO)
        analytics.trackOnboardingStepView(OnboardingStepName.INTRO)
        val store = draftStore ?: return
        viewModelScope.launch(exposureDispatcher) {
            store.markExposedIfNeeded(OnboardingVariant.Control)
            runCatching { onboardingAnalyticsDispatcher?.drain() }
        }
    }

    fun onContinue() {
        analytics.trackOnboardingStepComplete(OnboardingStepName.INTRO)
    }
}
