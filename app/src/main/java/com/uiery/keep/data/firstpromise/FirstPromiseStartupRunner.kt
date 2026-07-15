package com.uiery.keep.data.firstpromise

import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateReadResult
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import javax.inject.Inject
import javax.inject.Singleton

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
        val state = (runCatching { draftStore?.readStateResult() }.getOrNull() as?
            FirstPromiseStateReadResult.Available)?.state
        val shouldResumeCreation = state?.phase == FirstPromisePhase.Persisting ||
            (
                state?.phase == FirstPromisePhase.ResultEnabled &&
                    state.draft != null &&
                    state.scheduleState == FirstPromiseScheduleState.Enabled
            )
        if (shouldResumeCreation) {
            runCatching { creationCoordinator?.persistCurrentDraft() }
        }
        runCatching { dispatcher.drainAll() }
        runCatching { dispatcher.cleanupSentRows() }
    }
}
