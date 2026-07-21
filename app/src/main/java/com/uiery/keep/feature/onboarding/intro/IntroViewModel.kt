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
import kotlinx.coroutines.CancellationException
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
    private val viewTracked = AtomicBoolean(false)
    private val actionClaimed = AtomicBoolean(false)
    private val exposureAttemptLock = Any()
    private var exposureAttempt: CompletableDeferred<Boolean>? =
        if (draftStore == null) CompletableDeferred(true) else null

    fun onStepViewed() {
        if (viewTracked.compareAndSet(false, true)) {
            analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_INTRO)
            analytics.trackOnboardingStepView(OnboardingStepName.INTRO)
        }
        if (draftStore == null) return
        viewModelScope.launch(exposureDispatcher) {
            resolveExposure()
        }
    }

    fun onContinue() = intent {
        if (!actionClaimed.compareAndSet(false, true)) return@intent
        val resolved = try {
            resolveExposure()
        } catch (cancellation: CancellationException) {
            actionClaimed.set(false)
            throw cancellation
        }
        if (!resolved) {
            actionClaimed.set(false)
            return@intent
        }
        analytics.trackOnboardingStepComplete(OnboardingStepName.INTRO)
        postSideEffect(IntroSideEffect.NavigatePermissionSetting)
    }

    private suspend fun resolveExposure(): Boolean {
        val (attempt, ownsAttempt) = synchronized(exposureAttemptLock) {
            exposureAttempt?.let { it to false } ?: CompletableDeferred<Boolean>().let {
                exposureAttempt = it
                it to true
            }
        }
        if (!ownsAttempt) return attempt.await()

        return try {
            val resolved = persistExposure()
            attempt.complete(resolved)
            if (!resolved) clearAttempt(attempt)
            resolved
        } catch (cancellation: CancellationException) {
            attempt.completeExceptionally(cancellation)
            clearAttempt(attempt)
            throw cancellation
        } catch (_: Throwable) {
            attempt.complete(false)
            clearAttempt(attempt)
            false
        }
    }

    private suspend fun persistExposure(): Boolean {
        val store = draftStore ?: return true
        store.markExposedIfNeeded(OnboardingVariant.Control)
        check(FirstPromiseMilestone.Exposure in store.readState().trackedMilestones)
        try {
            onboardingAnalyticsDispatcher?.drain()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The exposure and its pending Analytics event are already durable for a later drain.
        }
        return true
    }

    private fun clearAttempt(attempt: CompletableDeferred<Boolean>) {
        synchronized(exposureAttemptLock) {
            if (exposureAttempt === attempt) exposureAttempt = null
        }
    }
}
