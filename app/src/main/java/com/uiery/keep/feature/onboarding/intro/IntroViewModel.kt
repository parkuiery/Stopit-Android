package com.uiery.keep.feature.onboarding.intro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

sealed interface IntroSideEffect {
    data object NavigatePermissionSetting : IntroSideEffect
}

@HiltViewModel
class IntroViewModel private constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore?,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher?,
    private val exposureDispatcher: CoroutineDispatcher,
) : ViewModel(), ContainerHost<Unit, IntroSideEffect> {
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

    override val container: Container<Unit, IntroSideEffect> = container(Unit)
    private val viewed = AtomicBoolean(false)
    private val actionClaimed = AtomicBoolean(false)
    private val exposureResolved = CompletableDeferred<Boolean>().apply {
        if (draftStore == null) complete(true)
    }

    fun onStepViewed() {
        if (!viewed.compareAndSet(false, true)) return
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_INTRO)
        analytics.trackOnboardingStepView(OnboardingStepName.INTRO)
        val store = draftStore ?: return
        viewModelScope.launch(exposureDispatcher) {
            val resolved = runCatching {
                store.markExposedIfNeeded(OnboardingVariant.Control)
                check(FirstPromiseMilestone.Exposure in store.readState().trackedMilestones)
            }.isSuccess
            if (resolved) runCatching { onboardingAnalyticsDispatcher?.drain() }
            exposureResolved.complete(resolved)
        }
    }

    fun onContinue() = intent {
        if (!actionClaimed.compareAndSet(false, true)) return@intent
        if (!exposureResolved.await()) {
            actionClaimed.set(false)
            return@intent
        }
        analytics.trackOnboardingStepComplete(OnboardingStepName.INTRO)
        postSideEffect(IntroSideEffect.NavigatePermissionSetting)
    }
}
