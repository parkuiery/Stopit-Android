package com.uiery.keep.analytics

import com.uiery.keep.data.firstpromise.FirstPromiseAnalyticsDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseAppCategoryBucket
import com.uiery.keep.data.firstpromise.FirstPromiseAttribution
import com.uiery.keep.data.firstpromise.FirstPromiseAttributionStore
import com.uiery.keep.data.firstpromise.FirstPromiseBlockSource
import com.uiery.keep.data.firstpromise.FirstPromiseBlockingMode
import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import com.uiery.keep.data.firstpromise.FirstPromiseElapsedSinceOpenBucket
import com.uiery.keep.data.firstpromise.FirstPromiseOutboxDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseRepository
import com.uiery.keep.data.firstpromise.FirstPromiseValueEventInput
import com.uiery.keep.data.firstpromise.FirstPromiseValueReservation
import com.uiery.keep.datastore.FirstPromisePracticeStore
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BlockAnalyticsRequest(
    val packageName: String,
    val blockSource: String,
    val routineId: String?,
    val goalLockId: String?,
)

data class BlockAnalyticsResult(
    val showFirstCoreActionFeedback: Boolean,
)

interface BlockDirectAnalyticsDelivery {
    fun appBlock(request: BlockAnalyticsRequest, origin: FirstPromiseOrigin?)
    fun coreAction(
        request: BlockAnalyticsRequest,
        kind: FirstPromiseCoreActionKind,
        elapsedSeconds: Long,
        origin: FirstPromiseOrigin?,
    )
}

@Singleton
class BlockAnalyticsCoordinator {
    private val attributionStore: FirstPromiseAttributionStore
    private val activePracticeAt: suspend (Long) -> FirstPromisePracticeToken?
    private val firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator
    private val outboxDispatcher: FirstPromiseOutboxDispatcher
    private val directDelivery: BlockDirectAnalyticsDelivery
    private val nowMillis: () -> Long
    private val decisionMutex = Mutex()

    @Inject
    constructor(
        attributionStore: FirstPromiseRepository,
        practiceStore: FirstPromisePracticeStore,
        firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator,
        outboxDispatcher: FirstPromiseAnalyticsDispatcher,
        analytics: KeepAnalytics,
        clock: Clock,
    ) : this(
        attributionStore = attributionStore,
        activePracticeAt = practiceStore::readActiveToken,
        firstCoreActionCoordinator = firstCoreActionCoordinator,
        outboxDispatcher = outboxDispatcher,
        directDelivery = KeepBlockDirectAnalyticsDelivery(analytics),
        nowMillis = clock::millis,
    )

    internal constructor(
        attributionStore: FirstPromiseAttributionStore,
        activePracticeAt: suspend (Long) -> FirstPromisePracticeToken?,
        firstCoreActionCoordinator: FirstCoreActionDeliveryCoordinator,
        outboxDispatcher: FirstPromiseOutboxDispatcher,
        directDelivery: BlockDirectAnalyticsDelivery,
        nowMillis: () -> Long,
    ) {
        this.attributionStore = attributionStore
        this.activePracticeAt = activePracticeAt
        this.firstCoreActionCoordinator = firstCoreActionCoordinator
        this.outboxDispatcher = outboxDispatcher
        this.directDelivery = directDelivery
        this.nowMillis = nowMillis
    }

    suspend fun track(
        request: BlockAnalyticsRequest,
        afterAppBlockTracked: () -> Unit = {},
    ): BlockAnalyticsResult = decisionMutex.withLock {
        val now = nowMillis()
        val attribution = resolveAttribution(request, now)
        if (attribution == null) return@withLock deliverDirect(request, null, now, afterAppBlockTracked)

        val initialDecision = firstCoreActionCoordinator.decide(now)
        val elapsedSeconds = elapsedSeconds(now, initialDecision.firstOpenTimestampMillis)
        val source = request.toDurableSource()
            ?: return@withLock deliverDirect(request, attribution.origin, now, afterAppBlockTracked)
        val reservation = attributionStore.reserveValueEvents(
            attribution = attribution,
            input = FirstPromiseValueEventInput(
                blockSource = source.first,
                blockingMode = source.second,
                categoryBucket = categoryBucket(request.packageName),
                elapsedBucket = FirstPromiseElapsedSinceOpenBucket.fromElapsedSeconds(elapsedSeconds),
                occurredAtMillis = now,
            ),
            allowFirst = initialDecision.kind == FirstPromiseCoreActionKind.First,
        )
        when (reservation) {
            is FirstPromiseValueReservation.Created -> {
                outboxDispatcher.drainDraft(attribution.draftId)
                BlockAnalyticsResult(
                    showFirstCoreActionFeedback = reservation.kind == FirstPromiseCoreActionKind.First,
                )
            }
            is FirstPromiseValueReservation.Existing -> {
                if (reservation.pending) outboxDispatcher.drainDraft(attribution.draftId)
                deliverDirect(request, attribution.origin, now, afterAppBlockTracked)
            }
            FirstPromiseValueReservation.OutsideWindow -> {
                // This also enforces the pending 10/20 barrier before direct canonical delivery.
                outboxDispatcher.drainDraft(attribution.draftId)
                deliverDirect(request, attribution.origin, now, afterAppBlockTracked)
            }
        }
    }

    private suspend fun resolveAttribution(
        request: BlockAnalyticsRequest,
        now: Long,
    ): FirstPromiseAttribution? {
        if (request.blockSource == AnalyticsBlockSource.ROUTINE) {
            request.routineId?.trim()?.toLongOrNull()?.let { routineId ->
                attributionStore.findRoutineAttribution(routineId)?.let { return it }
            }
        }
        if (request.blockSource == AnalyticsBlockSource.TIMED_LOCK) {
            val token = activePracticeAt(now) ?: return null
            return attributionStore.findDraftAttribution(
                token.draftId,
                FirstPromiseOrigin.FirstPromisePractice,
            )
        }
        return null
    }

    private suspend fun deliverDirect(
        request: BlockAnalyticsRequest,
        origin: FirstPromiseOrigin?,
        now: Long,
        afterAppBlockTracked: () -> Unit,
    ): BlockAnalyticsResult {
        val decision = firstCoreActionCoordinator.decide(now)
        val elapsedSeconds = elapsedSeconds(now, decision.firstOpenTimestampMillis)
        directDelivery.appBlock(request, origin)
        afterAppBlockTracked()
        directDelivery.coreAction(request, decision.kind, elapsedSeconds, origin)
        if (decision.kind == FirstPromiseCoreActionKind.First) {
            firstCoreActionCoordinator.markDirectFirstDelivered(decision.firstOpenTimestampMillis)
        }
        return BlockAnalyticsResult(decision.showFirstCoreActionFeedback)
    }
}

internal class KeepBlockDirectAnalyticsDelivery(
    private val analytics: KeepAnalytics,
) : BlockDirectAnalyticsDelivery {
    override fun appBlock(request: BlockAnalyticsRequest, origin: FirstPromiseOrigin?) {
        analytics.trackAppBlockIntercepted(
            blockSource = request.blockSource,
            blockedAppPackage = request.packageName,
            routineId = request.routineId,
            goalLockId = request.goalLockId,
            promiseOrigin = origin,
        )
    }

    override fun coreAction(
        request: BlockAnalyticsRequest,
        kind: FirstPromiseCoreActionKind,
        elapsedSeconds: Long,
        origin: FirstPromiseOrigin?,
    ) {
        if (origin != null) {
            analytics.logEvent(
                name = if (kind == FirstPromiseCoreActionKind.First) {
                    KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED
                } else {
                    KeepAnalyticsEvent.CORE_ACTION_COMPLETED
                },
                params = mapOf(
                    KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS to elapsedSeconds,
                    KeepAnalyticsParam.BLOCKING_MODE to request.blockSource,
                    KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to blockedAppCategoryBucketForPackage(request.packageName),
                    KeepAnalyticsParam.PROMISE_ORIGIN to origin.analyticsValue,
                ),
            )
        } else if (kind == FirstPromiseCoreActionKind.First) {
            analytics.trackFirstCoreActionCompleted(
                elapsedSeconds,
                request.blockSource,
                request.packageName,
                request.routineId,
                request.goalLockId,
            )
        } else {
            analytics.trackCoreActionCompleted(
                elapsedSeconds,
                request.blockSource,
                request.packageName,
                request.routineId,
                request.goalLockId,
            )
        }
    }
}

internal object NoAttributionStore : FirstPromiseAttributionStore {
    override suspend fun findRoutineAttribution(routineId: Long) = null
    override suspend fun findDraftAttribution(draftId: String, origin: FirstPromiseOrigin) = null
    override suspend fun hasFirstCoreActionReservation() = false
    override suspend fun reserveValueEvents(
        attribution: FirstPromiseAttribution,
        input: FirstPromiseValueEventInput,
        allowFirst: Boolean,
    ) = FirstPromiseValueReservation.OutsideWindow
}

internal object NoOpOutboxDispatcher : FirstPromiseOutboxDispatcher {
    override suspend fun drainAll() = Unit
    override suspend fun drainDraft(draftId: String) = Unit
    override suspend fun cleanupSentRows() = Unit
    override suspend fun creationEventsSent(draftId: String) = true
}

private fun BlockAnalyticsRequest.toDurableSource(): Pair<FirstPromiseBlockSource, FirstPromiseBlockingMode>? =
    when (blockSource) {
        AnalyticsBlockSource.ROUTINE -> FirstPromiseBlockSource.Routine to FirstPromiseBlockingMode.Routine
        AnalyticsBlockSource.TIMED_LOCK -> FirstPromiseBlockSource.TimedLock to FirstPromiseBlockingMode.TimedLock
        else -> null
    }

private fun categoryBucket(packageName: String): FirstPromiseAppCategoryBucket {
    val value = blockedAppCategoryBucketForPackage(packageName)
    return FirstPromiseAppCategoryBucket.entries.first { it.analyticsValue == value }
}

private fun elapsedSeconds(nowMillis: Long, firstOpenTimestampMillis: Long): Long {
    if (nowMillis <= firstOpenTimestampMillis) return 0L
    val elapsedMillis = runCatching { Math.subtractExact(nowMillis, firstOpenTimestampMillis) }
        .getOrDefault(Long.MAX_VALUE)
    return elapsedMillis / 1_000L
}
