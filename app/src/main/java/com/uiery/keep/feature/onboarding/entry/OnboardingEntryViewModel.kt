package com.uiery.keep.feature.onboarding.entry

import androidx.lifecycle.ViewModel
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.onboarding.experiment.FirebaseOnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentConfig
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentPolicy
import com.uiery.keep.feature.onboarding.experiment.OnboardingExperimentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

@HiltViewModel
class OnboardingEntryViewModel private constructor(
    private val draftStore: FirstPromiseDraftStore,
    private val experimentSnapshot: () -> OnboardingExperimentSnapshot,
    private val bucketProvider: () -> Int,
) : ViewModel(), ContainerHost<Unit, OnboardingEntrySideEffect> {
    @Inject
    constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: FirebaseOnboardingExperimentConfig,
    ) : this(
        draftStore = draftStore,
        experimentSnapshot = experimentConfig::snapshot,
        bucketProvider = { Random.nextInt(100) },
    )

    internal constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: OnboardingExperimentConfig,
        bucketProvider: () -> Int,
    ) : this(
        draftStore = draftStore,
        experimentSnapshot = experimentConfig::snapshot,
        bucketProvider = bucketProvider,
    )

    override val container: Container<Unit, OnboardingEntrySideEffect> = container(Unit)

    private val hasResolved = AtomicBoolean(false)

    fun resolve() {
        if (!hasResolved.compareAndSet(false, true)) return
        intent {
            val snapshot = experimentSnapshot()
            val initialEffect = resolveEntry(snapshot)
            val navigationEffect = if (initialEffect == OnboardingEntrySideEffect.WaitForPersistence) {
                awaitPersistenceNavigation(snapshot)
            } else {
                initialEffect
            }
            postSideEffect(navigationEffect)
        }
    }

    internal suspend fun resolveEntry(): OnboardingEntrySideEffect =
        resolveEntry(experimentSnapshot())

    private suspend fun resolveEntry(
        snapshot: OnboardingExperimentSnapshot,
    ): OnboardingEntrySideEffect {
        var state = draftStore.readState()
        if (state.assignment == null) {
            val assignment = OnboardingExperimentPolicy.assign(
                snapshot = snapshot,
                bucket = bucketProvider(),
            )
            state = draftStore.assignIfAbsent(
                variant = assignment,
                version = OnboardingAssignmentVersion.V1,
            )
        }

        if (state.assignment != OnboardingVariant.PromiseCoachV1) {
            return OnboardingEntrySideEffect.Navigate(OnboardingEntryDestination.Intro)
        }

        draftStore.recoverAfterRecreation()
        state = draftStore.readState()
        if (snapshot.emergencyDisabled) {
            val emergency = draftStore.applyEmergency()
            if (!emergency.navigationAllowed) {
                return OnboardingEntrySideEffect.WaitForPersistence
            }
            state = emergency.state
        }

        if (state.phase == FirstPromisePhase.Persisting) {
            return OnboardingEntrySideEffect.WaitForPersistence
        }

        return OnboardingEntrySideEffect.Navigate(
            destination = OnboardingEntryRoutePolicy.destinationFor(state.phase),
        )
    }

    private suspend fun awaitPersistenceNavigation(
        snapshot: OnboardingExperimentSnapshot,
    ): OnboardingEntrySideEffect.Navigate =
        draftStore.observeState()
            .filter { state -> state.phase != FirstPromisePhase.Persisting }
            .mapNotNull { resolveEntry(snapshot) as? OnboardingEntrySideEffect.Navigate }
            .first()
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
}

internal object OnboardingEntryRoutePolicy {
    fun destinationFor(phase: FirstPromisePhase): OnboardingEntryDestination = when (phase) {
        FirstPromisePhase.GoalPending -> OnboardingEntryDestination.GoalSelect
        FirstPromisePhase.UsageAccessPending -> OnboardingEntryDestination.UsageAccess
        FirstPromisePhase.Analyzing -> OnboardingEntryDestination.UsageAnalysis
        FirstPromisePhase.ManualSelectPending -> OnboardingEntryDestination.ManualAppSelect
        FirstPromisePhase.DraftReady,
        FirstPromisePhase.PersistFailed,
        -> OnboardingEntryDestination.PromiseProposal
        FirstPromisePhase.AccessibilityPending -> OnboardingEntryDestination.PromiseAccessibility
        FirstPromisePhase.NotificationPending -> OnboardingEntryDestination.PromiseNotification
        FirstPromisePhase.Persisting -> error("Persisting must wait for the in-flight transaction")
        FirstPromisePhase.SchedulePermissionRequired,
        FirstPromisePhase.ResultEnabled,
        FirstPromisePhase.ResultDisabled,
        -> OnboardingEntryDestination.PromiseResult
        FirstPromisePhase.CompletedEnabled,
        FirstPromisePhase.CompletedDisabled,
        -> OnboardingEntryDestination.Home
    }
}
