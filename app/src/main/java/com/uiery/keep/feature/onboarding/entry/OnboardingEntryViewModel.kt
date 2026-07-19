package com.uiery.keep.feature.onboarding.entry

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateCorruptedException
import com.uiery.keep.datastore.FirstPromiseStateReadResult
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentResolution
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@HiltViewModel
class OnboardingEntryViewModel private constructor(
    private val draftStore: FirstPromiseDraftStore,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher?,
    private val experimentResolve: suspend () -> OnboardingExperimentResolution,
) : ViewModel(), ContainerHost<Unit, OnboardingEntrySideEffect> {
    @Inject
    constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: OnboardingExperimentConfig,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) : this(
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        experimentResolve = experimentConfig::resolve,
    )

    internal constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: OnboardingExperimentConfig,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher? = null,
        @Suppress("UNUSED_PARAMETER") testConstructor: Unit = Unit,
    ) : this(
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        experimentResolve = experimentConfig::resolve,
    )

    override val container: Container<Unit, OnboardingEntrySideEffect> = container(Unit)

    private val hasResolved = AtomicBoolean(false)
    private var resolvedExperimentResolution: OnboardingExperimentResolution? = null

    fun resolve() {
        if (!hasResolved.compareAndSet(false, true)) return
        intent {
            val initialEffect = resolveEntry(cachedResolution = null)
            val finalEffect = if (initialEffect == OnboardingEntrySideEffect.WaitForPersistence) {
                awaitPersistenceNavigation()
            } else {
                initialEffect
            }
            postSideEffect(finalEffect)
        }
    }

    internal suspend fun resolveEntry(): OnboardingEntrySideEffect =
        resolveEntry(cachedResolution = null)

    private suspend fun resolveEntry(
        cachedResolution: OnboardingExperimentResolution?,
    ): OnboardingEntrySideEffect {
        return try {
            val initialRead = draftStore.readStateResult()
            if (initialRead == FirstPromiseStateReadResult.Corrupted) {
                return OnboardingEntrySideEffect.StateCorrupted
            }
            runCatching { onboardingAnalyticsDispatcher?.drain() }
            var state = (initialRead as FirstPromiseStateReadResult.Available).state
            if (state.assignment == OnboardingVariant.Control) {
                return OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro)
            }

            val resolution = if (state.assignment == null || state.assignment == OnboardingVariant.PromiseCoachV1) {
                cachedResolution ?: resolvedExperimentResolution ?: experimentResolve().also {
                    resolvedExperimentResolution = it
                }
            } else {
                OnboardingExperimentResolution()
            }

            if (state.assignment == null) {
                state = draftStore.assignIfAbsent(
                    variant = resolution.variant,
                    version = OnboardingAssignmentVersion.V1,
                )
            }

            if (state.assignment != OnboardingVariant.PromiseCoachV1) {
                return OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro)
            }

            draftStore.recoverAfterRecreation()
            state = draftStore.readState()
            if (resolution.remoteReadable && resolution.variant == OnboardingVariant.Control) {
                val emergency = draftStore.applyEmergency()
                if (!emergency.navigationAllowed) {
                    return OnboardingEntrySideEffect.WaitForPersistence
                }
                state = emergency.state
            }

            if (state.phase == FirstPromisePhase.Persisting) {
                return OnboardingEntrySideEffect.WaitForPersistence
            }

            OnboardingEntrySideEffect.Navigate(
                destination = OnboardingEntryRoutePolicy.destinationFor(state.phase),
            )
        } catch (_: FirstPromiseStateCorruptedException) {
            OnboardingEntrySideEffect.StateCorrupted
        }
    }

    private suspend fun awaitPersistenceNavigation(): OnboardingEntrySideEffect =
        (resolvedExperimentResolution ?: OnboardingExperimentResolution()).let { resolution ->
            draftStore.observeStateResult()
                .mapNotNull { readResult ->
                    when (readResult) {
                        FirstPromiseStateReadResult.Corrupted -> OnboardingEntrySideEffect.StateCorrupted
                        is FirstPromiseStateReadResult.Available ->
                            if (readResult.state.phase == FirstPromisePhase.Persisting) {
                                null
                            } else {
                                resolveEntry(cachedResolution = resolution)
                            }
                    }
                }
                .filter { effect -> effect != OnboardingEntrySideEffect.WaitForPersistence }
                .first()
        }
}

enum class OnboardingEntryDestination {
    Intro,
    GoalSelect,
    UsageAccess,
    UsageAnalysis,
    ManualAppSelect,
    PromiseProposal,
    PromiseAccessibility,
    PromiseNotification,
    PromiseResult,
    Home,
}

sealed interface OnboardingEntrySideEffect {
    data class Navigate(val destination: OnboardingEntryDestination) : OnboardingEntrySideEffect
    data object WaitForPersistence : OnboardingEntrySideEffect
    data object StateCorrupted : OnboardingEntrySideEffect
}

internal object OnboardingEntryRoutePolicy {
    fun destinationFor(phase: FirstPromisePhase): OnboardingEntryDestination = when (phase) {
        FirstPromisePhase.GoalPending -> OnboardingEntryDestination.GoalSelect
        FirstPromisePhase.UsageAccessPending -> OnboardingEntryDestination.UsageAccess
        FirstPromisePhase.Analyzing -> OnboardingEntryDestination.UsageAnalysis
        FirstPromisePhase.ManualSelectPending -> OnboardingEntryDestination.ManualAppSelect
        FirstPromisePhase.DraftReady -> OnboardingEntryDestination.PromiseProposal
        FirstPromisePhase.AccessibilityPending -> OnboardingEntryDestination.PromiseAccessibility
        FirstPromisePhase.NotificationPending -> OnboardingEntryDestination.PromiseNotification
        FirstPromisePhase.Persisting -> error("Persisting must wait for the in-flight transaction")
        FirstPromisePhase.SchedulePermissionRequired,
        FirstPromisePhase.PersistFailed,
        FirstPromisePhase.ResultEnabled,
        FirstPromisePhase.ResultDisabled,
        -> OnboardingEntryDestination.PromiseResult
        FirstPromisePhase.CompletedEnabled,
        FirstPromisePhase.CompletedDisabled,
        -> OnboardingEntryDestination.Home
    }
}
