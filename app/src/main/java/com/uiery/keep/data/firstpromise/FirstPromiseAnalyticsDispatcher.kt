package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsParam
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.database.dao.FirstPromiseAnalyticsOutboxDao
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.util.AppLogger
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SENT_RETENTION_DAYS = 30L
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

@Singleton
class FirstPromiseAnalyticsDispatcher : FirstPromiseOutboxDispatcher {
    private val store: FirstPromiseOutboxStore
    private val codec: FirstPromiseOutboxEventCodec
    private val analytics: KeepAnalytics
    private val clock: Clock
    private val invalidPayloadReporter: (String) -> Unit
    private val drainMutex = Mutex()

    @Inject
    constructor(
        dao: FirstPromiseAnalyticsOutboxDao,
        codec: FirstPromiseOutboxEventCodec,
        analytics: KeepAnalytics,
        clock: Clock,
    ) {
        store = RoomFirstPromiseOutboxStore(dao)
        this.codec = codec
        this.analytics = analytics
        this.clock = clock
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
    ) {
        this.store = store
        this.codec = codec
        this.analytics = analytics
        this.clock = clock
        this.invalidPayloadReporter = invalidPayloadReporter
    }

    override suspend fun drainAll() = drainMutex.withLock {
        store.pendingDraftIds().forEach { draftId ->
            runCatching { drainDraftUnlocked(draftId) }
        }
    }

    override suspend fun drainDraft(draftId: String) = drainMutex.withLock {
        drainDraftUnlocked(draftId)
    }

    override suspend fun cleanupSentRows() {
        store.deleteSentBefore(clock.millis() - SENT_RETENTION_DAYS * MILLIS_PER_DAY)
    }

    override suspend fun creationEventsSent(draftId: String): Boolean = store.creationEventsSent(draftId)

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
                    "promise_origin" to event.promiseOrigin.analyticsValue,
                ),
            )
            is FirstPromiseOutboxEvent.CoreAction -> analytics.logEvent(
                name = event.canonicalEventName,
                params = mapOf(
                    KeepAnalyticsParam.BLOCKING_MODE to event.blockingMode.analyticsValue,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to event.categoryBucket.analyticsValue,
                    "elapsed_since_first_open_bucket" to event.elapsedBucket.analyticsValue,
                    "promise_origin" to event.promiseOrigin.analyticsValue,
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
}

internal interface FirstPromiseOutboxStore {
    suspend fun pendingDraftIds(): List<String>
    suspend fun nextDeliverable(draftId: String): FirstPromiseAnalyticsOutboxEntity?
    suspend fun markSent(draftId: String, eventName: String, sentAtMillis: Long): Boolean
    suspend fun quarantine(draftId: String, eventName: String): Boolean
    suspend fun deleteSentBefore(cutoffMillis: Long)
    suspend fun creationEventsSent(draftId: String): Boolean
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
    override suspend fun deleteSentBefore(cutoffMillis: Long) = dao.deleteSentBefore(cutoffMillis)
    override suspend fun creationEventsSent(draftId: String): Boolean = dao.countSentCreationEvents(draftId) == 2
}
