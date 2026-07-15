package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsParam
import com.uiery.keep.analytics.FirstCoreActionDeliveryCoordinator
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.database.dao.FirstPromiseAnalyticsOutboxDao
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.util.AppLogger
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

private const val SENT_RETENTION_DAYS = 30L
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

@Singleton
class FirstPromiseAnalyticsDispatcher : FirstPromiseOutboxDispatcher {
    private val store: FirstPromiseOutboxStore
    private val codec: FirstPromiseOutboxEventCodec
    private val analytics: KeepAnalytics
    private val clock: Clock
    private val creationBarrier: FirstPromiseCreationBarrier
    private val invalidPayloadReporter: (String) -> Unit
    private val firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator?
    private val drainMutex = Mutex()

    @Inject
    constructor(
        dao: FirstPromiseAnalyticsOutboxDao,
        codec: FirstPromiseOutboxEventCodec,
        analytics: KeepAnalytics,
        clock: Clock,
        creationBarrier: FirstPromiseCreationBarrierStore,
        firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator,
    ) {
        store = RoomFirstPromiseOutboxStore(dao)
        this.codec = codec
        this.analytics = analytics
        this.clock = clock
        this.creationBarrier = creationBarrier
        this.firstCoreActionCoordinator = firstCoreActionCoordinator
        invalidPayloadReporter = { eventName ->
            AppLogger.debug(
                tag = "FirstPromiseOutbox",
                message = "Quarantined invalid outbox event type=$eventName",
            )
        }
    }

    internal constructor(
        store: FirstPromiseOutboxStore,
        codec: FirstPromiseOutboxEventCodec,
        analytics: KeepAnalytics,
        clock: Clock,
        invalidPayloadReporter: (String) -> Unit = {},
        creationBarrier: FirstPromiseCreationBarrier = InMemoryFirstPromiseCreationBarrier(),
        firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator? = null,
    ) {
        this.store = store
        this.codec = codec
        this.analytics = analytics
        this.clock = clock
        this.creationBarrier = creationBarrier
        this.firstCoreActionCoordinator = firstCoreActionCoordinator
        this.invalidPayloadReporter = invalidPayloadReporter
    }

    override suspend fun drainAll() = drainMutex.withLock {
        reconcileSentFirstCoreAction()
        store.pendingDraftIds().forEach { draftId ->
            try {
                drainDraftUnlocked(draftId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Isolate ordinary delivery failures so later drafts can still drain.
            }
        }
    }

    override suspend fun drainDraft(draftId: String) = drainMutex.withLock {
        reconcileSentFirstCoreAction()
        drainDraftUnlocked(draftId)
    }

    override suspend fun cleanupSentRows() {
        val creationBarrierReadyDraftIds = store.creationBarrierReadyDraftIds()
        creationBarrierReadyDraftIds.forEach { draftId ->
            creationBarrier.markComplete(draftId)
        }
        store.deleteSentBefore(
            cutoffMillis = clock.millis() - SENT_RETENTION_DAYS * MILLIS_PER_DAY,
            creationBarrierReadyDraftIds = creationBarrierReadyDraftIds,
        )
    }

    override suspend fun creationEventsSent(draftId: String): Boolean {
        if (creationBarrier.isComplete(draftId)) return true
        if (!store.creationEventsSent(draftId)) return false
        creationBarrier.markComplete(draftId)
        return true
    }

    override suspend fun analyticsBarrierComplete(draftId: String): Boolean =
        creationEventsSent(draftId) && store.valueEventsComplete(draftId)

    private suspend fun drainDraftUnlocked(draftId: String) {
        while (true) {
            val entity = store.nextDeliverable(draftId) ?: return
            val event = codec.decodeOrNull(entity)
            if (event == null) {
                store.quarantine(entity.draftId, entity.eventName)
                invalidPayloadReporter(entity.eventName)
                return
            }

            dispatch(event)
            if (!store.markSent(entity.draftId, entity.eventName, clock.millis())) return
            if (event is FirstPromiseOutboxEvent.CoreAction &&
                event.kind == FirstPromiseCoreActionKind.First
            ) {
                firstCoreActionCoordinator?.reconcileFirstDelivered(clock.millis())
            }
        }
    }

    private suspend fun reconcileSentFirstCoreAction() {
        if (firstCoreActionCoordinator != null && store.hasSentFirstCoreAction()) {
            firstCoreActionCoordinator.reconcileFirstDelivered(clock.millis())
        }
    }

    private fun dispatch(event: FirstPromiseOutboxEvent) {
        when (event) {
            is FirstPromiseOutboxEvent.RoutineSaved -> analytics.trackRoutineSaved(
                RoutineSavedAnalyticsPayload(
                    entrySurface = "onboarding",
                    creationSource = "onboarding_promise",
                    selectedAppCountBucket = "1",
                    repeatDaysBucket = event.repeatDaysBucket.analyticsValue,
                    timeWindowBucket = event.timeWindowBucket.analyticsValue,
                    scheduleState = event.scheduleState.analyticsValue,
                ),
            )
            is FirstPromiseOutboxEvent.FirstPromiseCreated -> analytics.trackFirstPromiseCreated(
                goalType = event.goal,
                source = event.source,
                scheduleState = event.scheduleState,
            )
            is FirstPromiseOutboxEvent.AppBlockIntercepted -> analytics.logEvent(
                name = event.canonicalEventName,
                params = mapOf(
                    KeepAnalyticsParam.BLOCK_SOURCE to event.blockSource.analyticsValue,
                    KeepAnalyticsParam.BLOCKING_MODE to event.blockingMode.analyticsValue,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to event.categoryBucket.analyticsValue,
                    KeepAnalyticsParam.PROMISE_ORIGIN to event.promiseOrigin.analyticsValue,
                ),
            )
            is FirstPromiseOutboxEvent.CoreAction -> analytics.logEvent(
                name = event.canonicalEventName,
                params = mapOf(
                    KeepAnalyticsParam.BLOCKING_MODE to event.blockingMode.analyticsValue,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to event.categoryBucket.analyticsValue,
                    KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET to event.elapsedBucket.analyticsValue,
                    KeepAnalyticsParam.PROMISE_ORIGIN to event.promiseOrigin.analyticsValue,
                ),
            )
        }
    }
}

interface FirstPromiseOutboxDispatcher {
    suspend fun drainAll()
    suspend fun drainDraft(draftId: String)
    suspend fun cleanupSentRows()
    suspend fun creationEventsSent(draftId: String): Boolean
    suspend fun analyticsBarrierComplete(draftId: String): Boolean = creationEventsSent(draftId)
}

internal interface FirstPromiseOutboxStore {
    suspend fun pendingDraftIds(): List<String>
    suspend fun nextDeliverable(draftId: String): FirstPromiseAnalyticsOutboxEntity?
    suspend fun markSent(draftId: String, eventName: String, sentAtMillis: Long): Boolean
    suspend fun quarantine(draftId: String, eventName: String): Boolean
    suspend fun deleteSentBefore(cutoffMillis: Long, creationBarrierReadyDraftIds: List<String>)
    suspend fun creationEventsSent(draftId: String): Boolean
    suspend fun creationBarrierReadyDraftIds(): List<String>
    suspend fun hasSentFirstCoreAction(): Boolean = false
    suspend fun valueEventsComplete(draftId: String): Boolean
}

private class RoomFirstPromiseOutboxStore(
    private val dao: FirstPromiseAnalyticsOutboxDao,
) : FirstPromiseOutboxStore {
    override suspend fun pendingDraftIds(): List<String> = dao.findPendingDraftIds()
    override suspend fun nextDeliverable(draftId: String): FirstPromiseAnalyticsOutboxEntity? =
        dao.findNextDeliverable(draftId)
    override suspend fun markSent(draftId: String, eventName: String, sentAtMillis: Long): Boolean =
        dao.markSent(draftId, eventName, sentAtMillis) == 1
    override suspend fun quarantine(draftId: String, eventName: String): Boolean =
        dao.quarantine(draftId, eventName) == 1
    override suspend fun deleteSentBefore(cutoffMillis: Long, creationBarrierReadyDraftIds: List<String>) =
        dao.deleteSentBefore(cutoffMillis, creationBarrierReadyDraftIds)
    override suspend fun creationEventsSent(draftId: String): Boolean = dao.countSentCreationEvents(draftId) == 2
    override suspend fun creationBarrierReadyDraftIds(): List<String> = dao.findCreationBarrierReadyDraftIds()
    override suspend fun hasSentFirstCoreAction(): Boolean = dao.countSentFirstCoreActions() > 0
    override suspend fun valueEventsComplete(draftId: String): Boolean =
        dao.countIncompleteValueEvents(draftId) == 0
}
