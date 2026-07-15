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
}

@Singleton
class FirstPromiseCreationCoordinator @Inject constructor(
    private val creator: FirstPromiseCreator,
    private val dispatcher: FirstPromiseOutboxDispatcher,
    private val draftStore: FirstPromiseDraftStore,
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
) {
    suspend fun persistCurrentDraft(): FirstPromisePersistenceResult {
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
            analytics.trackFirstLockConfigured(
                source = AnalyticsSource.ONBOARDING,
                selectedAppCount = 1,
            )
        }
        return FirstPromisePersistenceResult.Succeeded(creation)
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
