package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.FirstLockConfiguredDeliveryCoordinator
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.appselection.BlockExemptPackagePolicy
import com.uiery.keep.appselection.BlockExemptPackageProvider
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.model.RoutineModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult
    suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult
}

@Singleton
class FirstPromiseCreationCoordinator @Inject constructor(
    private val creator: FirstPromiseCreator,
    private val dispatcher: FirstPromiseOutboxDispatcher,
    private val draftStore: FirstPromiseDraftStore,
    private val blockingStateStore: BlockingStateStore,
    private val analytics: KeepAnalytics,
    private val blockExemptPackageProvider: BlockExemptPackageProvider = BlockExemptPackageProvider.None,
    private val firstLockDelivery: FirstLockConfiguredDeliveryCoordinator =
        FirstLockConfiguredDeliveryCoordinator(blockingStateStore, analytics),
) : FirstPromisePersistenceCoordinator {
    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? {
        val routineId = draftStore.readState().routineId ?: return null
        return creator.findExistingByRoutineId(routineId)
    }

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        val state = draftStore.readState()
        if (state.routineId != routineId) return FirstPromisePersistenceResult.MissingRoutine
        val creation = try {
            creator.findExistingByRoutineId(routineId)
                ?: return FirstPromisePersistenceResult.MissingRoutine
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        val draftId = creation.draftId ?: state.draft?.draftId
        if (creation.routine.isEnabled && draftId != null) {
            deliverAndTrackFirstLock(draftId, creation)
        }
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        val state = draftStore.readState()
        if (state.routineId != routineId) return FirstPromisePersistenceResult.MissingRoutine
        val creation = try {
            creator.finalizeExistingRoutine(routineId)
                ?: return FirstPromisePersistenceResult.MissingRoutine
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        try {
            draftStore.resolveScheduleState(routineId, creation.scheduleState)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        val draftId = creation.draftId ?: state.draft?.draftId
        if (draftId != null) deliverAndTrackFirstLock(draftId, creation)
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    override suspend fun persistCurrentDraft(): FirstPromisePersistenceResult {
        val draft = draftStore.readState().draft ?: return FirstPromisePersistenceResult.MissingDraft
        // Routine lockApplications never passes through BlockingStateStore, so an exempt package
        // would persist a routine that looks enabled and can never fire. Drafts recommended before
        // 1.7.12 can still name one.
        if (BlockExemptPackagePolicy.isExempt(draft.packageName, blockExemptPackageProvider.exemptPackages().homePackages)) {
            return FirstPromisePersistenceResult.Failed(
                IllegalStateException("First promise target ${draft.packageName} is exempt from blocking"),
            )
        }
        val creation = try {
            creator.createFirstPromise(draft, draft.toRoutine())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            try {
                draftStore.markPersistenceFailed()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Preserve the creation failure if local failure-state persistence is unavailable.
            }
            return FirstPromisePersistenceResult.Failed(failure)
        }

        val mappingMutation = try {
            draftStore.recordPersistenceMapping(creation.routineId, creation.scheduleState)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            return FirstPromisePersistenceResult.Failed(failure)
        }
        if (mappingMutation == FirstPromiseStateMutation.Rejected) {
            return FirstPromisePersistenceResult.Failed(
                IllegalStateException("Committed first-promise mapping was rejected by local state"),
            )
        }

        deliverAndTrackFirstLock(draft.draftId, creation)
        return FirstPromisePersistenceResult.Succeeded(creation)
    }

    private suspend fun deliverAndTrackFirstLock(
        draftId: String,
        creation: FirstPromiseCreationResult,
    ) {
        // Analytics delivery is at-least-once and must not roll back a committed routine. Startup
        // recovery drains the same rows if this attempt fails or the process dies.
        try {
            dispatcher.drainDraft(draftId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Startup recovery retries pending outbox delivery.
        }
        val creationEventsSent = try {
            dispatcher.creationEventsSent(draftId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (
            creation.scheduleState == FirstPromiseScheduleState.Enabled &&
            creation.schedulingSucceeded &&
            creationEventsSent
        ) {
            firstLockDelivery.trackIfNeeded(
                source = AnalyticsSource.ONBOARDING,
                selectedAppCount = 1,
            )
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
