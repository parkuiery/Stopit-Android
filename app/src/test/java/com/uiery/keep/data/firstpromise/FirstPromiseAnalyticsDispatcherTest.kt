package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val old = creationRows("old").first().copy(
            deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT,
            sentAtMillis = cutoff - 1,
        )
        val recent = creationRows("recent").first().copy(
            deliveryState = FirstPromiseOutboxEventCodec.DELIVERY_SENT,
            sentAtMillis = cutoff + 1,
        )
        val store = FakeOutboxStore(listOf(old, recent))

        FirstPromiseAnalyticsDispatcher(store, codec, DispatcherRecordingAnalytics(), clock).cleanupSentRows()

        assertEquals(listOf("recent"), store.rows.map { it.draftId })
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
}

private class FakeOutboxStore(
    initial: List<FirstPromiseAnalyticsOutboxEntity>,
    private var returnFalseFromNextMark: Boolean = false,
    private var throwFromNextMark: Boolean = false,
) : FirstPromiseOutboxStore {
    val rows = initial.toMutableList()
    private var rejectImmediateRedelivery = false

    fun allowNextDrain() {
        rejectImmediateRedelivery = false
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

    override suspend fun deleteSentBefore(cutoffMillis: Long) {
        rows.removeAll {
            it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT &&
                (it.sentAtMillis ?: Long.MAX_VALUE) < cutoffMillis
        }
    }

    override suspend fun creationEventsSent(draftId: String): Boolean = rows.count {
        it.draftId == draftId && it.sequence in setOf(10, 20) &&
            it.deliveryState == FirstPromiseOutboxEventCodec.DELIVERY_SENT
    } == 2

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

private class DispatcherRecordingAnalytics(
    private var failFirstCall: Boolean = false,
) : KeepAnalytics {
    val calls = mutableListOf<String>()
    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
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
