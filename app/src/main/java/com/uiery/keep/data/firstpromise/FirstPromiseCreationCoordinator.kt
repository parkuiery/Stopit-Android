package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.model.RoutineModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalTime

sealed interface FirstPromisePersistenceResult {
    data class Succeeded(val creation: FirstPromiseCreationResult) : FirstPromisePersistenceResult
    data class Failed(val cause: Throwable) : FirstPromisePersistenceResult
    data object MissingDraft : FirstPromisePersistenceResult
    data object MissingRoutine : FirstPromisePersistenceResult
}

interface FirstPromisePersistenceCoordinator {
    suspend fun persistCurrentDraft(): FirstPromisePersistenceResult
    suspend fun readCurrentMapping(): FirstPromiseCreationResult?
    suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult
}

@Singleton
class FirstPromiseCreationCoordinator @Inject constructor(
    private val creator: FirstPromiseCreator,
    private val dispatcher: FirstPromiseOutboxDispatcher,
    private val draftStore: FirstPromiseDraftStore,
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
) : FirstPromisePersistenceCoordinator {
    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? {
        val routineId = draftStore.readState().routineId ?: return null
        return creator.findExistingByRoutineId(routineId)
    }

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        val state = draftStore.readState()
        if (state.routineId != routineId) return FirstPromisePersistenceResult.MissingRoutine
        val creation = try {
            creator.finalizeExistingRoutine(routineId)
                ?: return FirstPromisePersistenceResult.MissingRoutine
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        try {
            draftStore.resolveScheduleState(routineId, creation.scheduleState)
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        state.draft?.let { draft ->
            deliverAndTrackFirstLock(draft, creation)
        }
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    override suspend fun persistCurrentDraft(): FirstPromisePersistenceResult {
        val draft = draftStore.readState().draft ?: return FirstPromisePersistenceResult.MissingDraft
        val creation = try {
            creator.createFirstPromise(draft, draft.toRoutine())
        } catch (failure: Throwable) {
            draftStore.markPersistenceFailed()
            return FirstPromisePersistenceResult.Failed(failure)
        }

        try {
            draftStore.recordPersistenceMapping(creation.routineId, creation.scheduleState)
        } catch (failure: Throwable) {
            draftStore.markPersistenceFailed()
            return FirstPromisePersistenceResult.Failed(failure)
        }

        deliverAndTrackFirstLock(draft, creation)
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    private suspend fun deliverAndTrackFirstLock(
        draft: FirstPromiseDraft,
        creation: FirstPromiseCreationResult,
    ) {
        // Analytics delivery is at-least-once and must not roll back a committed routine. Startup
        // recovery drains the same rows if this attempt fails or the process dies.
        runCatching { dispatcher.drainDraft(draft.draftId) }
        val creationEventsSent = runCatching {
            dispatcher.creationEventsSent(draft.draftId)
        }.getOrDefault(false)
        if (
            creation.scheduleState == FirstPromiseScheduleState.Enabled &&
            creation.schedulingSucceeded &&
            creationEventsSent &&
            blockingStateStore.markFirstLockConfiguredIfNeeded()
        ) {
            val analyticsResult = runCatching {
                analytics.trackFirstLockConfigured(
                    source = AnalyticsSource.ONBOARDING,
                    selectedAppCount = 1,
                )
            }
            if (analyticsResult.isFailure) {
                blockingStateStore.resetFirstLockConfiguredForRetry()
            }
        }
    }
}

private fun FirstPromiseDraft.toRoutine(): RoutineModel {
    val start = LocalTime(startMinutes / 60, startMinutes % 60)
    val endMinutes = (startMinutes + FIRST_PROMISE_DURATION_MINUTES) % MINUTES_PER_DAY
    return RoutineModel(
        id = 0L,
        name = appLabel,
        startTime = start,
        endTime = LocalTime(endMinutes / 60, endMinutes % 60),
        repeatDays = (1..7).joinToString("") { day -> if (day in repeatDays) "1" else "0" },
        lockApplications = listOf(packageName),
        isEnabled = true,
    )
}

private const val FIRST_PROMISE_DURATION_MINUTES = 30
private const val MINUTES_PER_DAY = 24 * 60
