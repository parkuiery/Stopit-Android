package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.KeepAnalyticsParam
import com.uiery.keep.analytics.FirstCoreActionDeliveryCoordinator
import com.uiery.keep.analytics.FirstCoreActionMarker
import com.uiery.keep.analytics.FirstCoreActionMarkerState
import com.uiery.keep.analytics.FirstCoreActionReservationStore
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FirstPromiseAnalyticsDispatcherTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-16T09:00:00Z"), ZoneOffset.UTC)
    private val codec = FirstPromiseOutboxEventCodec()

    @Test
    fun drainsEachDraftStrictlyInSequenceAndSecondStartupIsIdempotent() = runBlocking {
        val rows = listOf("b", "a").flatMap(::creationRows)
        val store = FakeOutboxStore(rows)
        val analytics = DispatcherRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        dispatcher.drainAll()
        dispatcher.drainAll()

        assertEquals(
            listOf("saved", "created", "saved", "created"),
            analytics.calls,
        )
        assertTrue(store.rows.all { it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT })
    }

    @Test
    fun durableValueDispatchUsesBucketOnlyAndNeverExportsExactElapsedSeconds() = runBlocking {
        val rows = listOf(
            codec.encode(
                "draft",
                FirstPromiseOutboxEvent.AppBlockIntercepted(
                    FirstPromiseBlockSource.Routine,
                    FirstPromiseBlockingMode.Routine,
                    FirstPromiseAppCategoryBucket.Productivity,
                    FirstPromiseOrigin.FirstPromiseRoutine,
                ),
                1L,
            ),
            codec.encode(
                "draft",
                FirstPromiseOutboxEvent.CoreAction(
                    FirstPromiseCoreActionKind.First,
                    FirstPromiseBlockingMode.Routine,
                    FirstPromiseAppCategoryBucket.Productivity,
                    FirstPromiseElapsedSinceOpenBucket.OneToFiveMinutes,
                    FirstPromiseOrigin.FirstPromiseRoutine,
                ),
                2L,
            ),
        )
        val analytics = DispatcherRecordingAnalytics()

        FirstPromiseAnalyticsDispatcher(FakeOutboxStore(rows), codec, analytics, clock)
            .drainDraft("draft")

        assertEquals(
            listOf(KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED, KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED),
            analytics.loggedEvents.map { it.first },
        )
        assertEquals(
            setOf(
                KeepAnalyticsParam.BLOCKING_MODE,
                KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET,
                KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET,
                KeepAnalyticsParam.PROMISE_ORIGIN,
            ),
            analytics.loggedEvents.last().second.keys,
        )
        assertEquals("1_5m", analytics.loggedEvents.last().second[KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET])
        assertTrue(analytics.loggedEvents.none { KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_SECONDS in it.second })
    }

    @Test
    fun sentFirstCoreRowReconcilesLegacyMarkerBeforeDrainAdvances() = runBlocking {
        val rows = listOf(
            codec.encode(
                "draft",
                FirstPromiseOutboxEvent.AppBlockIntercepted(
                    FirstPromiseBlockSource.Routine,
                    FirstPromiseBlockingMode.Routine,
                    FirstPromiseAppCategoryBucket.Unknown,
                    FirstPromiseOrigin.FirstPromiseRoutine,
                ),
                1L,
            ),
            codec.encode(
                "draft",
                FirstPromiseOutboxEvent.CoreAction(
                    FirstPromiseCoreActionKind.First,
                    FirstPromiseBlockingMode.Routine,
                    FirstPromiseAppCategoryBucket.Unknown,
                    FirstPromiseElapsedSinceOpenBucket.UnderMinute,
                    FirstPromiseOrigin.FirstPromiseRoutine,
                ),
                2L,
            ),
        )
        var marked = false
        val marker = object : FirstCoreActionMarker {
            override suspend fun read(nowMillis: Long) = FirstCoreActionMarkerState(7L, marked)
            override suspend fun mark(firstOpenTimestampMillis: Long) { marked = true }
        }
        val coordinator = FirstCoreActionDeliveryCoordinator(
            FirstCoreActionReservationStore { true },
            marker,
        )

        FirstPromiseAnalyticsDispatcher(
            FakeOutboxStore(rows),
            codec,
            DispatcherRecordingAnalytics(),
            clock,
            firstCoreActionCoordinator = coordinator,
        ).drainDraft("draft")

        assertTrue(marked)
    }

    @Test
    fun markerFailureAfterSentFirstCoreIsReconciledOnRetryWithoutResending() = runBlocking {
        val core = codec.encode(
            "draft",
            FirstPromiseOutboxEvent.CoreAction(
                FirstPromiseCoreActionKind.First,
                FirstPromiseBlockingMode.Routine,
                FirstPromiseAppCategoryBucket.Unknown,
                FirstPromiseElapsedSinceOpenBucket.UnderMinute,
                FirstPromiseOrigin.FirstPromiseRoutine,
            ),
            2L,
        )
        val store = FakeOutboxStore(listOf(core))
        var attempts = 0
        var marked = false
        val marker = object : FirstCoreActionMarker {
            override suspend fun read(nowMillis: Long) = FirstCoreActionMarkerState(7L, marked)
            override suspend fun mark(firstOpenTimestampMillis: Long) {
                attempts++
                if (attempts == 1) error("datastore unavailable")
                marked = true
            }
        }
        val analytics = DispatcherRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            store,
            codec,
            analytics,
            clock,
            firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
                FirstCoreActionReservationStore { true },
                marker,
            ),
        )

        assertTrue(runCatching { dispatcher.drainDraft("draft") }.isFailure)
        dispatcher.drainDraft("draft")

        assertTrue(marked)
        assertEquals(1, analytics.loggedEvents.size)
    }

    @Test
    fun analyticsBarrierStaysIncompleteForPendingOrQuarantinedRequiredRows() = runBlocking {
        val creation = creationRows("draft")
        val pendingStore = FakeOutboxStore(
            listOf(
                creation[0].copy(deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT),
                creation[1],
            ),
        )
        val pendingDispatcher = FirstPromiseAnalyticsDispatcher(
            pendingStore,
            codec,
            DispatcherRecordingAnalytics(),
            clock,
        )
        assertEquals(false, pendingDispatcher.analyticsBarrierComplete("draft"))

        pendingStore.quarantine("draft", creation[1].eventName)
        assertEquals(false, pendingDispatcher.analyticsBarrierComplete("draft"))
    }

    @Test
    fun durableCreationBarrierRemainsReadyAfterCreationRowsAreCleaned() = runBlocking {
        val barrier = FakeCreationBarrier().apply { completedDraftIds += "draft" }
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            FakeOutboxStore(emptyList()),
            codec,
            DispatcherRecordingAnalytics(),
            clock,
            creationBarrier = barrier,
        )

        assertEquals(true, dispatcher.analyticsBarrierComplete("draft"))
    }

    @Test
    fun durableCreationBarrierDoesNotIgnoreQuarantinedValueRow() = runBlocking {
        val barrier = FakeCreationBarrier().apply { completedDraftIds += "draft" }
        val quarantined = valueRows("draft").first().copy(
            deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_QUARANTINED,
        )
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            FakeOutboxStore(listOf(quarantined)),
            codec,
            DispatcherRecordingAnalytics(),
            clock,
            creationBarrier = barrier,
        )

        assertEquals(false, dispatcher.analyticsBarrierComplete("draft"))
    }

    @Test
    fun markSentFalseLeavesBarrierIncompleteAndPreventsValueOvertake() = runBlocking {
        val rows = creationRows("draft").map {
            it.copy(deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT)
        } + valueRows("draft")
        val store = FakeOutboxStore(rows, returnFalseFromNextMark = true)
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            store,
            codec,
            DispatcherRecordingAnalytics(),
            clock,
        )

        dispatcher.drainDraft("draft")

        assertEquals(false, dispatcher.analyticsBarrierComplete("draft"))
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first { it.sequence == 30 }.deliveryState)
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first { it.sequence == 40 }.deliveryState)
    }

    @Test
    fun sequenceThirtyFailureRetryAlwaysSendsThirtyBeforeForty() = runBlocking {
        val rows = creationRows("draft").map {
            it.copy(deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT)
        } + valueRows("draft")
        val analytics = DispatcherRecordingAnalytics(failFirstLoggedEvent = true)
        val dispatcher = FirstPromiseAnalyticsDispatcher(FakeOutboxStore(rows), codec, analytics, clock)

        assertTrue(runCatching { dispatcher.drainDraft("draft") }.isFailure)
        dispatcher.drainDraft("draft")

        assertEquals(
            listOf(
                KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED,
                KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED,
            ),
            analytics.loggedEvents.map { it.first },
        )
        assertTrue(dispatcher.analyticsBarrierComplete("draft"))
    }

    @Test
    fun analyticsFailureLeavesPendingAndRetryMaySendAtLeastOnce() = runBlocking {
        val store = FakeOutboxStore(creationRows("draft"))
        val analytics = DispatcherRecordingAnalytics(failFirstCall = true)
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        runCatching { dispatcher.drainDraft("draft") }
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first().deliveryState)

        dispatcher.drainDraft("draft")
        assertEquals(listOf("saved", "saved", "created"), analytics.calls)
    }

    @Test
    fun drainAllIsolatesOneDraftFailureAndContinuesLaterDrafts() = runBlocking {
        val store = FakeOutboxStore(creationRows("first") + creationRows("second"))
        val analytics = DispatcherRecordingAnalytics(failFirstCall = true)
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        dispatcher.drainAll()

        assertEquals(
            FirstPromiseOutboxEventCodec.DELIVERY_PENDING,
            store.rows.first { it.draftId == "first" && it.sequence == 10 }.deliveryState,
        )
        assertTrue(
            store.rows.filter { it.draftId == "second" }
                .all { it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT },
        )
        assertEquals(listOf("saved", "saved", "created"), analytics.calls)
    }

    @Test
    fun drainAllNeverIsolatesCoroutineCancellation() {
        val store = FakeOutboxStore(creationRows("draft"))
        val analytics = DispatcherRecordingAnalytics(
            firstFailure = CancellationException("cancel drain"),
        )
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        assertThrows(CancellationException::class.java) {
            runBlocking { dispatcher.drainAll() }
        }

        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first().deliveryState)
    }

    @Test
    fun falseSentMarkerStopsCurrentDrainAndNextDrainRetriesAtLeastOnce() = runBlocking {
        val store = FakeOutboxStore(creationRows("draft"), returnFalseFromNextMark = true)
        val analytics = DispatcherRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        val firstDrain = runCatching { dispatcher.drainDraft("draft") }

        assertTrue(firstDrain.isSuccess)
        assertEquals(listOf("saved"), analytics.calls)
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first().deliveryState)

        store.allowNextDrain()
        dispatcher.drainDraft("draft")
        assertEquals(listOf("saved", "saved", "created"), analytics.calls)
    }

    @Test
    fun thrownSentMarkerStopsCurrentDrainAndNextDrainRetriesAtLeastOnce() = runBlocking {
        val store = FakeOutboxStore(creationRows("draft"), throwFromNextMark = true)
        val analytics = DispatcherRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock)

        assertTrue(runCatching { dispatcher.drainDraft("draft") }.isFailure)
        assertEquals(listOf("saved"), analytics.calls)
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.first().deliveryState)

        dispatcher.drainDraft("draft")
        assertEquals(listOf("saved", "saved", "created"), analytics.calls)
    }

    @Test
    fun invalidPayloadIsQuarantinedReportedAndNeverAllowsLaterSequence() = runBlocking {
        val invalid = creationRows("draft").first().copy(payloadJson = "{broken")
        val store = FakeOutboxStore(listOf(invalid, creationRows("draft").last()))
        val reports = mutableListOf<String>()
        val analytics = DispatcherRecordingAnalytics()
        val dispatcher = FirstPromiseAnalyticsDispatcher(store, codec, analytics, clock, reports::add)

        dispatcher.drainDraft("draft")

        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_QUARANTINED, store.rows.first().deliveryState)
        assertEquals(FirstPromiseOutboxEventCodec.DELIVERY_PENDING, store.rows.last().deliveryState)
        assertEquals(emptyList<String>(), analytics.calls)
        assertEquals(listOf("routine_saved"), reports)
    }

    @Test
    fun cleanupDeletesOnlySentRowsOlderThanThirtyDays() = runBlocking {
        val cutoff = clock.millis() - 30L * 24 * 60 * 60 * 1000
        val old = creationRows("old").map {
            it.copy(
                deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT,
                sentAtMillis = cutoff - 1,
            )
        }
        val recent = creationRows("recent").first().copy(
            deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT,
            sentAtMillis = cutoff + 1,
        )
        val store = FakeOutboxStore(old + recent)

        FirstPromiseAnalyticsDispatcher(store, codec, DispatcherRecordingAnalytics(), clock).cleanupSentRows()

        assertEquals(listOf("recent"), store.rows.map { it.draftId })
    }

    @Test
    fun cleanupKeepsPartialCreationEvidenceUntilBarrierCanBeCompleted() = runBlocking {
        val oldSent = creationRows("draft").first().copy(
            deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT,
            sentAtMillis = clock.millis() - 31L * 24 * 60 * 60 * 1000,
        )
        val pending = creationRows("draft").last()
        val store = FakeOutboxStore(listOf(oldSent, pending))

        FirstPromiseAnalyticsDispatcher(store, codec, DispatcherRecordingAnalytics(), clock).cleanupSentRows()

        assertEquals(listOf(oldSent, pending), store.rows)
    }

    @Test
    fun durableCreationBarrierSurvivesSentRowCleanupWithoutInferringAbsence() = runBlocking {
        val store = FakeOutboxStore(creationRows("draft"))
        val barrier = FakeCreationBarrier()
        val dispatcher = FirstPromiseAnalyticsDispatcher(
            store = store,
            codec = codec,
            analytics = DispatcherRecordingAnalytics(),
            clock = clock,
            creationBarrier = barrier,
        )

        dispatcher.drainDraft("draft")
        assertTrue(dispatcher.creationEventsSent("draft"))
        assertEquals(setOf("draft"), barrier.completedDraftIds)

        store.ageSentRows(clock.millis() - 31L * 24 * 60 * 60 * 1000)
        dispatcher.cleanupSentRows()

        assertTrue(store.rows.isEmpty())
        assertTrue(dispatcher.creationEventsSent("draft"))
        assertTrue(barrier.completedDraftIds.contains("draft"))
    }

    private fun creationRows(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> = listOf(
        codec.encode(
            draftId,
            FirstPromiseOutboxEvent.RoutineSaved(
                FirstPromiseRepeatDaysBucket.Seven,
                FirstPromiseTimeWindowBucket.Night,
                FirstPromiseScheduleState.Enabled,
            ),
            1L,
        ),
        codec.encode(
            draftId,
            FirstPromiseOutboxEvent.FirstPromiseCreated(
                FirstPromiseGoal.Focus,
                FirstPromiseSource.Personalized,
                FirstPromiseScheduleState.Enabled,
            ),
            2L,
        ),
    )

    private fun valueRows(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> = listOf(
        codec.encode(
            draftId,
            FirstPromiseOutboxEvent.AppBlockIntercepted(
                FirstPromiseBlockSource.Routine,
                FirstPromiseBlockingMode.Routine,
                FirstPromiseAppCategoryBucket.Unknown,
                FirstPromiseOrigin.FirstPromiseRoutine,
            ),
            3L,
        ),
        codec.encode(
            draftId,
            FirstPromiseOutboxEvent.CoreAction(
                FirstPromiseCoreActionKind.First,
                FirstPromiseBlockingMode.Routine,
                FirstPromiseAppCategoryBucket.Unknown,
                FirstPromiseElapsedSinceOpenBucket.UnderMinute,
                FirstPromiseOrigin.FirstPromiseRoutine,
            ),
            4L,
        ),
    )
}

internal class FakeOutboxStore(
    initial: List<FirstPromiseAnalyticsOutboxEntity>,
    private var returnFalseFromNextMark: Boolean = false,
    private var throwFromNextMark: Boolean = false,
) : FirstPromiseOutboxStore {
    val rows = initial.toMutableList()
    private var rejectImmediateRedelivery = false

    fun allowNextDrain() {
        rejectImmediateRedelivery = false
    }

    fun ageSentRows(sentAtMillis: Long) {
        rows.replaceAll { row ->
            if (row.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT) {
                row.copy(sentAtMillis = sentAtMillis)
            } else {
                row
            }
        }
    }

    override suspend fun pendingDraftIds(): List<String> = rows
        .filter { it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_PENDING }
        .groupBy { it.draftId }
        .toList()
        .sortedBy { (_, values) -> values.minOf { it.sequence } }
        .map { it.first }

    override suspend fun nextDeliverable(draftId: String): FirstPromiseAnalyticsOutboxEntity? {
        if (rejectImmediateRedelivery) {
            rejectImmediateRedelivery = false
            error("same drain attempted immediate redelivery")
        }
        val draftRows = rows.filter { it.draftId == draftId }
        return draftRows
            .filter { it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_PENDING }
            .sortedBy { it.sequence }
            .firstOrNull { candidate ->
                draftRows.none {
                    it.sequence < candidate.sequence && it.deliveryState != FirstPromiseOutboxEventCodec.DELIVERY_SENT
                }
            }
    }

    override suspend fun markSent(draftId: String, eventName: String, sentAtMillis: Long): Boolean {
        if (throwFromNextMark) {
            throwFromNextMark = false
            throw IllegalStateException("marker unavailable")
        }
        if (returnFalseFromNextMark) {
            returnFalseFromNextMark = false
            rejectImmediateRedelivery = true
            return false
        }
        return replace(draftId, eventName) {
            it.copy(deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT, sentAtMillis = sentAtMillis)
        }
    }

    override suspend fun quarantine(draftId: String, eventName: String): Boolean =
        replace(draftId, eventName) { it.copy(deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_QUARANTINED) }

    override suspend fun deleteSentBefore(
        cutoffMillis: Long,
        creationBarrierReadyDraftIds: List<String>,
    ) {
        rows.removeAll {
            it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT &&
                (it.sentAtMillis ?: Long.MAX_VALUE) < cutoffMillis &&
                (it.sequence !in setOf(10, 20) || it.draftId in creationBarrierReadyDraftIds)
        }
    }

    override suspend fun creationEventsSent(draftId: String): Boolean = rows.count {
        it.draftId == draftId && it.sequence in setOf(10, 20) &&
            it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT
    } == 2

    override suspend fun creationBarrierReadyDraftIds(): List<String> = rows
        .filter {
            it.sequence in setOf(10, 20) &&
                it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT
        }
        .groupingBy { it.draftId }
        .eachCount()
        .filterValues { it == 2 }
        .keys
        .toList()

    override suspend fun hasSentFirstCoreAction(): Boolean = rows.any {
        it.sequence == 40 &&
            it.canonicalEventName == KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED &&
            it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT
    }

    override suspend fun valueEventsComplete(draftId: String): Boolean {
        val draftRows = rows.filter { it.draftId == draftId }
        return draftRows.none {
            it.sequence in setOf(30, 40) &&
                it.deliveryState != FirstPromiseOutboxEventCodec.DELIVERY_SENT
        }
    }

    private fun replace(
        draftId: String,
        eventName: String,
        transform: (FirstPromiseAnalyticsOutboxEntity) -> FirstPromiseAnalyticsOutboxEntity,
    ): Boolean {
        val index = rows.indexOfFirst {
            it.draftId == draftId && it.eventName == eventName &&
                it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_PENDING
        }
        if (index < 0) return false
        rows[index] = transform(rows[index])
        return true
    }
}

internal class FakeCreationBarrier : FirstPromiseCreationBarrier {
    val completedDraftIds = mutableSetOf<String>()

    override suspend fun isComplete(draftId: String): Boolean = draftId in completedDraftIds

    override suspend fun markComplete(draftId: String) {
        completedDraftIds += draftId
    }
}

private class DispatcherRecordingAnalytics(
    private var failFirstCall: Boolean = false,
    private var firstFailure: Throwable? = null,
    private var failFirstLoggedEvent: Boolean = false,
) : KeepAnalytics {
    val calls = mutableListOf<String>()
    val loggedEvents = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun logEvent(name: String, params: Map<String, Any?>) {
        loggedEvents += name to params
        if (failFirstLoggedEvent) {
            failFirstLoggedEvent = false
            error("analytics unavailable")
        }
    }
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackRoutineSaved(payload: RoutineSavedAnalyticsPayload) {
        calls += "saved"
        firstFailure?.let { failure ->
            firstFailure = null
            throw failure
        }
        if (failFirstCall) {
            failFirstCall = false
            error("analytics unavailable")
        }
    }

    override fun trackFirstPromiseCreated(
        goalType: FirstPromiseGoal,
        source: FirstPromiseSource,
        scheduleState: FirstPromiseScheduleState,
    ) {
        calls += "created"
    }
}
