package com.uiery.keep.analytics

import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingOnboardingAnalyticsEvent
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Delivers durable onboarding milestones in FIFO order.
 *
 * Delivery is at-least-once: an event is removed only after the Analytics call returns. A process
 * crash after the SDK accepts an event but before the state edit can retry that event on restart;
 * funnel reporting therefore uses unique-user deduplication.
 */
@Singleton
class FirstPromiseOnboardingAnalyticsDispatcher @Inject constructor(
    private val store: FirstPromiseDraftStore,
    private val analytics: KeepAnalytics,
) {
    private val drainMutex = Mutex()

    suspend fun drain() = drainMutex.withLock {
        while (true) {
            val event = store.readState().pendingOnboardingAnalyticsEvents.firstOrNull() ?: return@withLock
            dispatch(event, store.readState())
            check(store.acknowledgePendingAnalyticsEvent(event) is FirstPromiseStateMutation.Changed) {
                "Pending onboarding Analytics head changed before acknowledgement"
            }
        }
    }

    private fun dispatch(
        event: PendingOnboardingAnalyticsEvent,
        state: FirstPromiseOnboardingState,
    ) {
        when (event) {
            PendingOnboardingAnalyticsEvent.ExperimentExposureControlV1 ->
                analytics.trackOnboardingExperimentExposed(
                    OnboardingVariant.Control,
                    OnboardingAssignmentVersion.V1,
                )

            PendingOnboardingAnalyticsEvent.ExperimentExposurePromiseCoachV1 ->
                analytics.trackOnboardingExperimentExposed(
                    OnboardingVariant.PromiseCoachV1,
                    OnboardingAssignmentVersion.V1,
                )

            PendingOnboardingAnalyticsEvent.GoalSelectStepView ->
                analytics.trackOnboardingStepView(OnboardingStepName.GOAL_SELECT)

            PendingOnboardingAnalyticsEvent.GoalSelectStepComplete ->
                analytics.trackOnboardingStepComplete(OnboardingStepName.GOAL_SELECT)

            PendingOnboardingAnalyticsEvent.UsageAccessStepView ->
                analytics.trackOnboardingStepView(OnboardingStepName.USAGE_ACCESS)

            PendingOnboardingAnalyticsEvent.UsageAccessStepComplete ->
                analytics.trackOnboardingStepComplete(OnboardingStepName.USAGE_ACCESS)

            PendingOnboardingAnalyticsEvent.SelectAppStepComplete ->
                analytics.trackOnboardingStepComplete(OnboardingStepName.SELECT_APP)

            PendingOnboardingAnalyticsEvent.AppSelectionCompletedSingle ->
                analytics.trackAppSelectionCompleted(selectedAppCount = 1, isOnboarding = true)

            PendingOnboardingAnalyticsEvent.PromiseProposalStepView ->
                analytics.trackOnboardingStepView(OnboardingStepName.PROMISE_PROPOSAL)

            PendingOnboardingAnalyticsEvent.PromiseRecommendationShown -> {
                val draft = checkNotNull(state.draft)
                val reason = checkNotNull(state.recommendationReasonRef)
                analytics.trackPromiseRecommendationShown(
                    goalType = state.goal,
                    patternType = reason.patternType,
                    source = draft.source,
                )
            }

            PendingOnboardingAnalyticsEvent.PromiseProposalStepComplete ->
                analytics.trackOnboardingStepComplete(OnboardingStepName.PROMISE_PROPOSAL)

            PendingOnboardingAnalyticsEvent.PromiseResultStepView ->
                analytics.trackOnboardingStepView(OnboardingStepName.PROMISE_RESULT)

            PendingOnboardingAnalyticsEvent.PromiseResultStepComplete ->
                analytics.trackOnboardingStepComplete(OnboardingStepName.PROMISE_RESULT)
        }
    }
}
