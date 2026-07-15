package com.uiery.keep.feature.onboarding.entry

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateCorruptedException
import com.uiery.keep.datastore.FirstPromiseStateReadResult
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
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher?,
    private val experimentSnapshot: () -> OnboardingExperimentSnapshot,
    private val bucketProvider: () -> Int,
) : ViewModel(), ContainerHost<Unit, OnboardingEntrySideEffect> {
    @Inject
    constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: FirebaseOnboardingExperimentConfig,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) : this(
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        experimentSnapshot = experimentConfig::snapshot,
        bucketProvider = { Random.nextInt(100) },
    )

    internal constructor(
        draftStore: FirstPromiseDraftStore,
        experimentConfig: OnboardingExperimentConfig,
        bucketProvider: () -> Int,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher? = null,
    ) : this(
        draftStore = draftStore,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        experimentSnapshot = experimentConfig::snapshot,
        bucketProvider = bucketProvider,
    )

    override val container: Container<Unit, OnboardingEntrySideEffect> = container(Unit)

    private val hasResolved = AtomicBoolean(false)

    fun resolve() {
        if (!hasResolved.compareAndSet(false, true)) return
        intent {
            runCatching { onboardingAnalyticsDispatcher?.drain() }
            val snapshot = experimentSnapshot()
            val initialEffect = resolveEntry(snapshot)
            val finalEffect = if (initialEffect == OnboardingEntrySideEffect.WaitForPersistence) {
                awaitPersistenceNavigation(snapshot)
            } else {
                initialEffect
            }
            postSideEffect(finalEffect)
        }
    }

    internal suspend fun resolveEntry(): OnboardingEntrySideEffect =
        resolveEntry(experimentSnapshot())

    private suspend fun resolveEntry(
        snapshot: OnboardingExperimentSnapshot,
    ): OnboardingEntrySideEffect {
        return try {
            val initialRead = draftStore.readStateResult()
            if (initialRead == FirstPromiseStateReadResult.Corrupted) {
                return OnboardingEntrySideEffect.StateCorrupted
            }
            var state = (initialRead as FirstPromiseStateReadResult.Available).state
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

            OnboardingEntrySideEffect.Navigate(
                destination = OnboardingEntryRoutePolicy.destinationFor(state.phase),
            )
        } catch (_: FirstPromiseStateCorruptedException) {
            OnboardingEntrySideEffect.StateCorrupted
        }
    }

    private suspend fun awaitPersistenceNavigation(
        snapshot: OnboardingExperimentSnapshot,
    ): OnboardingEntrySideEffect =
        draftStore.observeStateResult()
            .mapNotNull { readResult ->
                when (readResult) {
                    FirstPromiseStateReadResult.Corrupted -> OnboardingEntrySideEffect.StateCorrupted
                    is FirstPromiseStateReadResult.Available ->
                        if (readResult.state.phase == FirstPromisePhase.Persisting) {
                            null
                        } else {
                            resolveEntry(snapshot)
                        }
                }
            }
            .filter { effect -> effect != OnboardingEntrySideEffect.WaitForPersistence }
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
    data object StateCorrupted : OnboardingEntrySideEffect
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
