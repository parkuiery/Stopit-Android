package com.uiery.keep.data.firstpromise

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

    @Inject
    constructor(
        dispatcher: FirstPromiseOutboxDispatcher,
        draftStore: FirstPromiseDraftStore,
        creationCoordinator: FirstPromisePersistenceCoordinator,
    ) {
        this.dispatcher = dispatcher
        this.draftStore = draftStore
        this.creationCoordinator = creationCoordinator
    }

    internal constructor(dispatcher: FirstPromiseOutboxDispatcher) {
        this.dispatcher = dispatcher
        draftStore = null
        creationCoordinator = null
    }

    suspend fun run() {
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
