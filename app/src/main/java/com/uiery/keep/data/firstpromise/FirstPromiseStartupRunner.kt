package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.FirstLockConfiguredDeliveryCoordinator
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateReadResult
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class FirstPromiseStartupRunner {
    private val dispatcher: FirstPromiseOutboxDispatcher
    private val draftStore: FirstPromiseDraftStore?
    private val creationCoordinator: FirstPromisePersistenceCoordinator?
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher?
    private val firstLockConfiguredDelivery: FirstLockConfiguredDeliveryCoordinator?

    @Inject
    constructor(
        dispatcher: FirstPromiseOutboxDispatcher,
        draftStore: FirstPromiseDraftStore,
        creationCoordinator: FirstPromisePersistenceCoordinator,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
        firstLockConfiguredDelivery: FirstLockConfiguredDeliveryCoordinator,
    ) {
        this.dispatcher = dispatcher
        this.draftStore = draftStore
        this.creationCoordinator = creationCoordinator
        this.onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher
        this.firstLockConfiguredDelivery = firstLockConfiguredDelivery
    }

    internal constructor(dispatcher: FirstPromiseOutboxDispatcher) {
        this.dispatcher = dispatcher
        draftStore = null
        creationCoordinator = null
        onboardingAnalyticsDispatcher = null
        firstLockConfiguredDelivery = null
    }

    internal constructor(
        dispatcher: FirstPromiseOutboxDispatcher,
        firstLockConfiguredDelivery: FirstLockConfiguredDeliveryCoordinator,
    ) {
        this.dispatcher = dispatcher
        draftStore = null
        creationCoordinator = null
        onboardingAnalyticsDispatcher = null
        this.firstLockConfiguredDelivery = firstLockConfiguredDelivery
    }

    internal constructor(
        dispatcher: FirstPromiseOutboxDispatcher,
        draftStore: FirstPromiseDraftStore,
        creationCoordinator: FirstPromisePersistenceCoordinator,
    ) {
        this.dispatcher = dispatcher
        this.draftStore = draftStore
        this.creationCoordinator = creationCoordinator
        onboardingAnalyticsDispatcher = null
        firstLockConfiguredDelivery = null
    }

    internal constructor(
        dispatcher: FirstPromiseOutboxDispatcher,
        draftStore: FirstPromiseDraftStore,
        creationCoordinator: FirstPromisePersistenceCoordinator,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    ) {
        this.dispatcher = dispatcher
        this.draftStore = draftStore
        this.creationCoordinator = creationCoordinator
        this.onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher
        firstLockConfiguredDelivery = null
    }

    suspend fun run() {
        recoverSafely { firstLockConfiguredDelivery?.deliverPending() }
        val state = try {
            (draftStore?.readStateResult() as? FirstPromiseStateReadResult.Available)?.state
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        when {
            state?.phase == FirstPromisePhase.Persisting ->
                recoverSafely { creationCoordinator?.persistCurrentDraft() }
            state?.routineId != null && state.scheduleState == FirstPromiseScheduleState.Enabled ->
                recoverSafely { creationCoordinator?.reconcileExistingRoutine(state.routineId) }
        }
        recoverSafely { onboardingAnalyticsDispatcher?.drain() }
        recoverSafely { dispatcher.drainAll() }
        recoverSafely { dispatcher.cleanupSentRows() }
    }

    private suspend fun recoverSafely(block: suspend () -> Any?) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // A later process start retries durable recovery work.
        }
    }
}
