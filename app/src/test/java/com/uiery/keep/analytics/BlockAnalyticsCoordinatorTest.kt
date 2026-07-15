package com.uiery.keep.analytics

import com.uiery.keep.data.firstpromise.FirstPromiseCoreActionKind
import com.uiery.keep.data.firstpromise.FirstPromiseAttribution
import com.uiery.keep.data.firstpromise.FirstPromiseAttributionStore
import com.uiery.keep.data.firstpromise.FirstPromiseValueEventInput
import com.uiery.keep.data.firstpromise.FirstPromiseValueReservation
import com.uiery.keep.data.firstpromise.FirstPromiseOutboxDispatcher
import com.uiery.keep.datastore.FirstPromisePracticeToken
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockAnalyticsCoordinatorTest {
    @Test
    fun ordinaryBlockPreservesCanonicalOrderAndFirstFeedback() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = BlockAnalyticsCoordinator(
            attributionStore = NoAttributionStore,
            activePracticeAt = { null },
            firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
                reservationStore = { false },
                marker = FakeMarker(),
            ),
            outboxDispatcher = NoOpOutboxDispatcher,
            directDelivery = RecordingDirectDelivery(calls),
            nowMillis = { 1_000L },
        )

        val result = coordinator.track(
            BlockAnalyticsRequest(
                packageName = "com.example.blocked",
                blockSource = AnalyticsBlockSource.MANUAL_KEEP,
                routineId = null,
                goalLockId = null,
            ),
        )

        assertEquals(listOf("app_block_intercepted", "first_core_action_completed"), calls)
        assertEquals(true, result.showFirstCoreActionFeedback)
    }

    @Test
    fun routineMappingReservesAndDrainsTypedValuePair() = runBlocking {
        val store = RecordingAttributionStore(
            routine = FirstPromiseAttribution("routine-draft", FirstPromiseOrigin.FirstPromiseRoutine, 0L),
        )
        val drains = mutableListOf<String>()
        val coordinator = coordinator(store, { null }, drains)

        val result = coordinator.track(
            BlockAnalyticsRequest(
                "com.notion.id",
                AnalyticsBlockSource.ROUTINE,
                "42",
                null,
            ),
        )

        assertEquals(FirstPromiseOrigin.FirstPromiseRoutine, store.reservedAttribution?.origin)
        assertEquals("routine-draft", drains.single())
        assertEquals(true, result.showFirstCoreActionFeedback)
    }

    @Test
    fun activePracticeTokenMapsToPracticeOrigin() = runBlocking {
        val store = RecordingAttributionStore(
            draft = FirstPromiseAttribution("practice-draft", FirstPromiseOrigin.FirstPromisePractice, 0L),
        )
        val coordinator = coordinator(
            store,
            { FirstPromisePracticeToken("practice-draft", 0L, 10_000L) },
            mutableListOf(),
        )

        coordinator.track(
            BlockAnalyticsRequest("com.example.video", AnalyticsBlockSource.TIMED_LOCK, null, null),
        )

        assertEquals(FirstPromiseOrigin.FirstPromisePractice, store.reservedAttribution?.origin)
    }

    @Test
    fun outsideWindowWaitsForDraftDrainBeforeDirectCanonicalEvents() = runBlocking {
        val order = mutableListOf<String>()
        val store = RecordingAttributionStore(
            routine = FirstPromiseAttribution("draft", FirstPromiseOrigin.FirstPromiseRoutine, 0L),
            reservation = FirstPromiseValueReservation.OutsideWindow,
        )
        val coordinator = BlockAnalyticsCoordinator(
            attributionStore = store,
            activePracticeAt = { null },
            firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
                reservationStore = { false },
                marker = FakeMarker(),
            ),
            outboxDispatcher = object : FirstPromiseOutboxDispatcher {
                override suspend fun drainAll() = Unit
                override suspend fun drainDraft(draftId: String) { order += "drain" }
                override suspend fun cleanupSentRows() = Unit
                override suspend fun creationEventsSent(draftId: String) = true
                override suspend fun analyticsBarrierComplete(draftId: String) = true
            },
            directDelivery = RecordingDirectDelivery(order),
            nowMillis = { 86_400_000L },
        )

        coordinator.track(BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null))

        assertEquals(listOf("drain", "app_block_intercepted", "first_core_action_completed"), order)
    }

    @Test
    fun outsideWindowSuppressesDirectEventsUntilCreationBarrierIsReady() = runBlocking {
        val order = mutableListOf<String>()
        val dispatcher = ToggleBarrierDispatcher(ready = false, order = order)
        val store = RecordingAttributionStore(
            routine = FirstPromiseAttribution("draft", FirstPromiseOrigin.FirstPromiseRoutine, 0L),
            reservation = FirstPromiseValueReservation.OutsideWindow,
        )
        val coordinator = BlockAnalyticsCoordinator(
            store,
            { null },
            FirstCoreActionDeliveryCoordinator({ false }, FakeMarker()),
            dispatcher,
            RecordingDirectDelivery(order),
            { 86_400_000L },
        )

        val blocked = coordinator.track(
            BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null),
        )
        assertEquals(false, blocked.showFirstCoreActionFeedback)
        assertEquals(listOf("drain"), order)

        dispatcher.ready = true
        val delivered = coordinator.track(
            BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null),
        )
        assertEquals(true, delivered.showFirstCoreActionFeedback)
        assertEquals(
            listOf("drain", "drain", "app_block_intercepted", "first_core_action_completed"),
            order,
        )
    }

    @Test
    fun existingPendingValuePairSuppressesDirectRepeatUntilThirtyAndFortyAreSent() = runBlocking {
        val order = mutableListOf<String>()
        val dispatcher = ToggleBarrierDispatcher(ready = false, order = order)
        val store = RecordingAttributionStore(
            routine = FirstPromiseAttribution("draft", FirstPromiseOrigin.FirstPromiseRoutine, 0L),
            reservation = FirstPromiseValueReservation.Existing(pending = true),
            firstReserved = true,
        )
        val coordinator = BlockAnalyticsCoordinator(
            store,
            { null },
            FirstCoreActionDeliveryCoordinator(store::hasFirstCoreActionReservation, FakeMarker()),
            dispatcher,
            RecordingDirectDelivery(order),
            { 1_000L },
        )

        val blocked = coordinator.track(
            BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null),
        )
        assertEquals(false, blocked.showFirstCoreActionFeedback)
        assertEquals(listOf("drain"), order)

        dispatcher.ready = true
        coordinator.track(BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null))
        assertEquals(listOf("drain", "drain", "app_block_intercepted", "core_action_completed"), order)
    }

    @Test
    fun reconstructedCoordinatorUsesSharedDurableReservationAndSuppressesFirstFeedback() = runBlocking {
        val sharedStore = RecordingAttributionStore(
            routine = FirstPromiseAttribution("draft", FirstPromiseOrigin.FirstPromiseRoutine, 0L),
        )
        val firstProcess = coordinator(sharedStore, { null }, mutableListOf())
        val first = firstProcess.track(
            BlockAnalyticsRequest("com.example", AnalyticsBlockSource.ROUTINE, "1", null),
        )
        assertEquals(true, first.showFirstCoreActionFeedback)

        val callsAfterRestart = mutableListOf<String>()
        val reconstructed = BlockAnalyticsCoordinator(
            sharedStore,
            { null },
            FirstCoreActionDeliveryCoordinator(sharedStore::hasFirstCoreActionReservation, FakeMarker()),
            NoOpOutboxDispatcher,
            RecordingDirectDelivery(callsAfterRestart),
            { 2_000L },
        )
        val ordinary = reconstructed.track(
            BlockAnalyticsRequest("com.other", AnalyticsBlockSource.MANUAL_KEEP, null, null),
        )

        assertEquals(false, ordinary.showFirstCoreActionFeedback)
        assertEquals(listOf("app_block_intercepted", "core_action_completed"), callsAfterRestart)
    }

    @Test
    fun concurrentAttributableAndOrdinaryCallsExposeExactlyOneFirstFeedback() = runBlocking {
        val kinds = mutableListOf<FirstPromiseCoreActionKind>()
        var reserved = false
        val firstReservationEntered = CountDownLatch(1)
        val releaseFirstReservation = CountDownLatch(1)
        val store = object : FirstPromiseAttributionStore {
            override suspend fun findRoutineAttribution(routineId: Long) = FirstPromiseAttribution(
                "draft-$routineId",
                FirstPromiseOrigin.FirstPromiseRoutine,
                0L,
            )
            override suspend fun findDraftAttribution(draftId: String, origin: FirstPromiseOrigin) = null
            override suspend fun hasFirstCoreActionReservation() = reserved
            override suspend fun reserveValueEvents(
                attribution: FirstPromiseAttribution,
                input: FirstPromiseValueEventInput,
                allowFirst: Boolean,
            ): FirstPromiseValueReservation {
                val kind = if (allowFirst && !reserved) FirstPromiseCoreActionKind.First else FirstPromiseCoreActionKind.Repeat
                if (kind == FirstPromiseCoreActionKind.First) {
                    reserved = true
                    firstReservationEntered.countDown()
                    check(releaseFirstReservation.await(5, TimeUnit.SECONDS))
                }
                kinds += kind
                return FirstPromiseValueReservation.Created(kind)
            }
        }
        val marker = FakeMarker()
        val coordinator = BlockAnalyticsCoordinator(
            store,
            { null },
            FirstCoreActionDeliveryCoordinator({ store.hasFirstCoreActionReservation() }, marker),
            NoOpOutboxDispatcher,
            RecordingDirectDelivery(mutableListOf()),
            { 1_000L },
        )

        val first = async(Dispatchers.Default) {
            coordinator.track(BlockAnalyticsRequest("a", AnalyticsBlockSource.ROUTINE, "1", null))
        }
        check(firstReservationEntered.await(5, TimeUnit.SECONDS))

        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val requests = listOf(
            BlockAnalyticsRequest("b", AnalyticsBlockSource.ROUTINE, "2", null),
            BlockAnalyticsRequest("c", AnalyticsBlockSource.MANUAL_KEEP, null, null),
        )
        val jobs = requests.map { request ->
            async(Dispatchers.Default) {
                ready.countDown()
                check(start.await(5, TimeUnit.SECONDS))
                coordinator.track(request)
            }
        }
        check(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        releaseFirstReservation.countDown()
        val results = listOf(first.await()) + jobs.awaitAll()

        assertEquals(1, results.count { it.showFirstCoreActionFeedback })
        assertEquals(1, kinds.count { it == FirstPromiseCoreActionKind.First })
    }

    private fun coordinator(
        store: FirstPromiseAttributionStore,
        practice: suspend (Long) -> FirstPromisePracticeToken?,
        drains: MutableList<String>,
    ) = BlockAnalyticsCoordinator(
        attributionStore = store,
        activePracticeAt = practice,
        firstCoreActionCoordinator = FirstCoreActionDeliveryCoordinator(
            reservationStore = { false },
            marker = FakeMarker(),
        ),
        outboxDispatcher = object : FirstPromiseOutboxDispatcher {
            override suspend fun drainAll() = Unit
            override suspend fun drainDraft(draftId: String) { drains += draftId }
            override suspend fun cleanupSentRows() = Unit
            override suspend fun creationEventsSent(draftId: String) = true
            override suspend fun analyticsBarrierComplete(draftId: String) = true
        },
        directDelivery = RecordingDirectDelivery(mutableListOf()),
        nowMillis = { 1_000L },
    )

    private class FakeMarker : FirstCoreActionMarker {
        private var tracked = false
        override suspend fun read(nowMillis: Long) = FirstCoreActionMarkerState(0L, tracked)
        override suspend fun mark(firstOpenTimestampMillis: Long) { tracked = true }
    }

    private class RecordingDirectDelivery(private val calls: MutableList<String>) : BlockDirectAnalyticsDelivery {
        override fun appBlock(request: BlockAnalyticsRequest, origin: FirstPromiseOrigin?) {
            calls += "app_block_intercepted"
        }

        override fun coreAction(
            request: BlockAnalyticsRequest,
            kind: FirstPromiseCoreActionKind,
            elapsedSeconds: Long,
            origin: FirstPromiseOrigin?,
        ) {
            calls += if (kind == FirstPromiseCoreActionKind.First) {
                "first_core_action_completed"
            } else {
                "core_action_completed"
            }
        }
    }
}

private class RecordingAttributionStore(
    private val routine: FirstPromiseAttribution? = null,
    private val draft: FirstPromiseAttribution? = null,
    private val reservation: FirstPromiseValueReservation? = null,
    private var firstReserved: Boolean = false,
) : FirstPromiseAttributionStore {
    var reservedAttribution: FirstPromiseAttribution? = null
    override suspend fun findRoutineAttribution(routineId: Long) = routine
    override suspend fun findDraftAttribution(draftId: String, origin: FirstPromiseOrigin) = draft
    override suspend fun hasFirstCoreActionReservation() = firstReserved
    override suspend fun reserveValueEvents(
        attribution: FirstPromiseAttribution,
        input: FirstPromiseValueEventInput,
        allowFirst: Boolean,
    ): FirstPromiseValueReservation {
        reservedAttribution = attribution
        if (reservation != null) return reservation
        val kind = if (allowFirst && !firstReserved) {
            firstReserved = true
            FirstPromiseCoreActionKind.First
        } else {
            FirstPromiseCoreActionKind.Repeat
        }
        return FirstPromiseValueReservation.Created(kind)
    }
}

private class ToggleBarrierDispatcher(
    var ready: Boolean,
    private val order: MutableList<String>,
) : FirstPromiseOutboxDispatcher {
    override suspend fun drainAll() = Unit
    override suspend fun drainDraft(draftId: String) { order += "drain" }
    override suspend fun cleanupSentRows() = Unit
    override suspend fun creationEventsSent(draftId: String) = ready
    override suspend fun analyticsBarrierComplete(draftId: String) = ready
}
