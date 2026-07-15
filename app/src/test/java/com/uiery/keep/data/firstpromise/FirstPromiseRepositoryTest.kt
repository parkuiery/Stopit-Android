package com.uiery.keep.data.firstpromise

import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.database.entity.FirstPromiseEntity
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class FirstPromiseRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-16T09:00:00Z"), ZoneOffset.UTC)
    private val draft = FirstPromiseDraft(
        draftId = "draft-1",
        goal = FirstPromiseGoal.Focus,
        packageName = "com.example.focus",
        appLabel = "Focus",
        startMinutes = 22 * 60,
        repeatDays = (1..7).toSet(),
        source = FirstPromiseSource.Personalized,
    )
    private val routine = RoutineModel(
        id = 0,
        name = "First promise",
        startTime = LocalTime(22, 0),
        endTime = LocalTime(22, 30),
        repeatDays = "1111111",
        lockApplications = listOf(draft.packageName),
        isEnabled = true,
    )

    @Test
    fun sameDraftAndConcurrentDuplicateTapsCommitOneRoutineMappingAndOrderedPair() = runBlocking<Unit> {
        val store = FakeAtomicStore(initialReadGate = CountDownLatch(2))
        val scheduler = scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.Scheduled)
        val repository = repository(store, scheduler)

        val results = List(2) {
            async(Dispatchers.Default) { repository.createFirstPromise(draft, routine) }
        }.awaitAll()

        assertEquals(1, results.map { it.routineId }.distinct().size)
        assertEquals(1, store.routines.size)
        assertEquals(1, store.mappings.size)
        assertEquals(listOf(10, 20), store.outbox.map { it.sequence }.sorted())
        Mockito.verify(scheduler, Mockito.times(1))
            .scheduleRoutine(Mockito.any(RoutineModel::class.java) ?: routine)
    }

    @Test
    fun existingMappingDoesNotRecreateOutboxRowsAfterSentRowsAreCleanedUp() = runBlocking<Unit> {
        val store = FakeAtomicStore()
        val scheduler = scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.Scheduled)
        val repository = repository(store, scheduler)
        val first = repository.createFirstPromise(draft, routine)
        store.outbox.clear()

        val retried = repository.createFirstPromise(draft, routine)

        assertEquals(first.routineId, retried.routineId)
        assertEquals(1, store.routines.size)
        assertEquals(1, store.mappings.size)
        assertTrue(store.outbox.isEmpty())
        Mockito.verify(scheduler, Mockito.times(1)).scheduleRoutine(Mockito.any(RoutineModel::class.java) ?: routine)
    }

    @Test
    fun exactAlarmResolutionHappensBeforeInsertAndMissingPermissionPersistsDisabledState() = runBlocking {
        val calls = mutableListOf<String>()
        val store = FakeAtomicStore(onInsertRoutine = { calls += "insert" })
        val scheduler = scheduler(
            canSchedule = false,
            scheduleResult = RoutineScheduleResult.NotEnabled,
            onCanSchedule = { calls += "resolve" },
        )

        val result = repository(store, scheduler).createFirstPromise(draft, routine)

        assertEquals(listOf("resolve", "insert"), calls)
        assertFalse(store.routines.single().isEnabled)
        assertEquals(FirstPromiseScheduleState.DisabledExactAlarmMissing, result.scheduleState)
        assertEquals(
            FirstPromiseScheduleState.DisabledExactAlarmMissing,
            (FirstPromiseOutboxEventCodec().decode(store.outbox.last()) as FirstPromiseOutboxEvent.FirstPromiseCreated).scheduleState,
        )
    }

    @Test
    fun schedulingReturnedDisabledRoutineIsFinalizedBeforeOutboxCommit() = runBlocking {
        val store = FakeAtomicStore()
        val result = repository(
            store,
            scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.InvalidRoutine),
        ).createFirstPromise(draft, routine)

        assertFalse(store.routines.single().isEnabled)
        assertEquals(FirstPromiseScheduleState.DisabledUnknown, result.scheduleState)
        assertFalse(result.schedulingSucceeded)
    }

    @Test
    fun routineSavedUsesFinalizedOvernightWindow() = runBlocking {
        val overnightDraft = draft.copy(startMinutes = 23 * 60 + 45)
        val overnightRoutine = routine.copy(
            startTime = LocalTime(23, 45),
            endTime = LocalTime(0, 15),
        )
        val store = FakeAtomicStore()

        repository(
            store,
            scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.Scheduled),
        ).createFirstPromise(overnightDraft, overnightRoutine)

        val saved = FirstPromiseOutboxEventCodec().decode(store.outbox.first()) as FirstPromiseOutboxEvent.RoutineSaved
        assertEquals(FirstPromiseTimeWindowBucket.Overnight, saved.timeWindowBucket)
    }

    @Test
    fun queryAndFinalizeExistingRoutineKeepTheSameRoutineId() = runBlocking<Unit> {
        val store = FakeAtomicStore()
        val disabledRepository = repository(
            store,
            scheduler(canSchedule = false, scheduleResult = RoutineScheduleResult.NotEnabled),
        )
        val created = disabledRepository.createFirstPromise(draft, routine)
        val scheduledIds = mutableListOf<Long>()
        val enabledScheduler = scheduler(
            canSchedule = true,
            scheduleResult = RoutineScheduleResult.Scheduled,
            onSchedule = { scheduledIds += it.id },
        )
        val enabledRepository = repository(store, enabledScheduler)

        val byDraft = enabledRepository.findExistingByDraftId(draft.draftId)
        val byRoutine = enabledRepository.findExistingByRoutineId(created.routineId)
        val finalized = enabledRepository.finalizeExistingRoutine(created.routineId)

        assertEquals(created.routineId, byDraft?.routineId)
        assertEquals(created.routineId, byRoutine?.routineId)
        assertEquals(created.routineId, finalized?.routineId)
        assertEquals(1, store.routines.size)
        assertTrue(store.routines.single().isEnabled)
        assertEquals(listOf(created.routineId), scheduledIds)
    }

    @Test
    fun postScheduleTransactionFailureCancelsAlarmAndLeavesNoCommittedRows() = runBlocking {
        val store = FakeAtomicStore(failAfterOutboxInsert = true)
        val scheduler = scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.Scheduled)

        val failure = runCatching { repository(store, scheduler).createFirstPromise(draft, routine) }

        assertTrue(failure.isFailure)
        Mockito.verify(scheduler).cancelRoutine(1L)
        assertTrue(store.routines.isEmpty())
        assertTrue(store.mappings.isEmpty())
        assertTrue(store.outbox.isEmpty())
    }

    @Test
    fun scheduleThrowAfterInstallingAlarmStillCancelsTheOwnedRoutine() = runBlocking {
        val store = FakeAtomicStore()
        val scheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(
            scheduler.scheduleRoutine(Mockito.any(RoutineModel::class.java) ?: routine),
        ).thenThrow(IllegalStateException("throw after install"))

        val failure = runCatching { repository(store, scheduler).createFirstPromise(draft, routine) }

        assertTrue(failure.isFailure)
        Mockito.verify(scheduler).cancelRoutine(1L)
        assertTrue(store.routines.isEmpty())
    }

    @Test
    fun valueReservationUsesExclusiveDayBoundaryAndTypedBucketPayload() = runBlocking {
        val store = FakeAtomicStore()
        val repository = repository(store, scheduler(true, RoutineScheduleResult.Scheduled))
        repository.createFirstPromise(draft, routine)
        val attribution = repository.findRoutineAttribution(1L)!!

        val inside = repository.reserveValueEvents(
            attribution,
            FirstPromiseValueEventInput(
                FirstPromiseBlockSource.Routine,
                FirstPromiseBlockingMode.Routine,
                FirstPromiseAppCategoryBucket.Productivity,
                FirstPromiseElapsedSinceOpenBucket.OneToFiveMinutes,
                attribution.createdAtMillis + 86_399_999L,
            ),
            allowFirst = true,
        )

        assertEquals(FirstPromiseValueReservation.Created(FirstPromiseCoreActionKind.First), inside)
        assertEquals(listOf(10, 20, 30, 40), store.outbox.map { it.sequence })
        val core = FirstPromiseOutboxEventCodec().decode(store.outbox.last()) as FirstPromiseOutboxEvent.CoreAction
        assertEquals(FirstPromiseElapsedSinceOpenBucket.OneToFiveMinutes, core.elapsedBucket)
        assertTrue(store.outbox.last().payloadJson.contains("elapsed_since_first_open_bucket"))
        assertFalse(store.outbox.last().payloadJson.contains("elapsed_since_first_open_seconds"))
    }

    @Test
    fun exactDayBoundaryCreatesNoValueRows() = runBlocking {
        val store = FakeAtomicStore()
        val repository = repository(store, scheduler(true, RoutineScheduleResult.Scheduled))
        repository.createFirstPromise(draft, routine)
        val attribution = repository.findRoutineAttribution(1L)!!

        val result = repository.reserveValueEvents(
            attribution,
            FirstPromiseValueEventInput(
                FirstPromiseBlockSource.Routine,
                FirstPromiseBlockingMode.Routine,
                FirstPromiseAppCategoryBucket.Unknown,
                FirstPromiseElapsedSinceOpenBucket.OverFiveMinutes,
                attribution.createdAtMillis + 86_400_000L,
            ),
            allowFirst = true,
        )

        assertEquals(FirstPromiseValueReservation.OutsideWindow, result)
        assertEquals(listOf(10, 20), store.outbox.map { it.sequence })
    }

    @Test
    fun concurrentDraftReservationsSelectExactlyOneGlobalFirstCoreEvent() = runBlocking {
        val store = FakeAtomicStore()
        store.mappings += FirstPromiseEntity("a", 1L, "focus", "personalized", clock.millis())
        store.mappings += FirstPromiseEntity("b", 2L, "focus", "personalized", clock.millis())
        val repository = repository(store, scheduler(true, RoutineScheduleResult.Scheduled))
        val input = FirstPromiseValueEventInput(
            FirstPromiseBlockSource.Routine,
            FirstPromiseBlockingMode.Routine,
            FirstPromiseAppCategoryBucket.Unknown,
            FirstPromiseElapsedSinceOpenBucket.UnderMinute,
            clock.millis(),
        )

        listOf("a", "b").map { id ->
            async(Dispatchers.Default) {
                repository.reserveValueEvents(
                    FirstPromiseAttribution(id, FirstPromiseOrigin.FirstPromiseRoutine, clock.millis()),
                    input,
                    allowFirst = true,
                )
            }
        }.awaitAll()

        val coreKinds = store.outbox.filter { it.sequence == 40 }
            .map { FirstPromiseOutboxEventCodec().decode(it) as FirstPromiseOutboxEvent.CoreAction }
            .map { it.kind }
        assertEquals(1, coreKinds.count { it == FirstPromiseCoreActionKind.First })
        assertEquals(1, coreKinds.count { it == FirstPromiseCoreActionKind.Repeat })
    }

    private fun repository(store: FirstPromiseRepositoryStorage, scheduler: RoutineScheduler) =
        FirstPromiseRepository(
            storage = store,
            exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
            codec = FirstPromiseOutboxEventCodec(),
            clock = clock,
        )

    private fun scheduler(
        canSchedule: Boolean,
        scheduleResult: RoutineScheduleResult,
        onCanSchedule: () -> Unit = {},
        onSchedule: (RoutineModel) -> Unit = {},
    ): RoutineScheduler = Mockito.mock(RoutineScheduler::class.java).also { scheduler ->
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenAnswer {
            onCanSchedule()
            canSchedule
        }
        Mockito.`when`(
            scheduler.scheduleRoutine(Mockito.any(RoutineModel::class.java) ?: routine),
        ).thenAnswer { invocation ->
            onSchedule(invocation.getArgument(0))
            scheduleResult
        }
    }
}

private class FakeAtomicStore(
    private val onInsertRoutine: () -> Unit = {},
    private val failAfterOutboxInsert: Boolean = false,
    private val initialReadGate: CountDownLatch? = null,
) : FirstPromiseRepositoryStorage {
    private val mutex = Mutex()
    val routines = mutableListOf<RoutineModel>()
    val mappings = mutableListOf<FirstPromiseEntity>()
    val outbox = mutableListOf<FirstPromiseAnalyticsOutboxEntity>()

    override suspend fun findMapping(draftId: String): FirstPromiseEntity? {
        val result = mappings.firstOrNull { it.draftId == draftId }
        initialReadGate?.takeIf { it.count > 0L }?.let { gate ->
            gate.countDown()
            check(gate.await(5, TimeUnit.SECONDS)) { "duplicate creation calls did not overlap" }
        }
        return result
    }

    override suspend fun findMappingByRoutineId(routineId: Long): FirstPromiseEntity? =
        mappings.firstOrNull { it.routineId == routineId }

    override suspend fun findRoutine(id: Long): RoutineModel =
        routines.first { it.id == id }

    override suspend fun findOutbox(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> =
        outbox.filter { it.draftId == draftId }

    override suspend fun hasFirstCoreActionReservation(): Boolean = outbox.any {
        it.sequence == 40 &&
            it.canonicalEventName == com.uiery.keep.analytics.KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED &&
            it.deliveryState != FirstPromiseOutboxEventCodec.DELIVERY_QUARANTINED
    }

    override suspend fun <T> inTransaction(block: suspend FirstPromiseRepositoryStorage.() -> T): T = mutex.withLock {
        val routineSnapshot = routines.toList()
        val mappingSnapshot = mappings.toList()
        val outboxSnapshot = outbox.toList()
        try {
            block(this)
        } catch (failure: Throwable) {
            routines.clear(); routines += routineSnapshot
            mappings.clear(); mappings += mappingSnapshot
            outbox.clear(); outbox += outboxSnapshot
            throw failure
        }
    }

    override suspend fun insertRoutine(routine: RoutineModel): Long {
        onInsertRoutine()
        val id = (routines.maxOfOrNull { it.id } ?: 0L) + 1L
        routines += routine.copy(id = id)
        return id
    }

    override suspend fun updateRoutine(routine: RoutineModel) {
        routines.replaceAll { if (it.id == routine.id) routine else it }
    }

    override suspend fun insertMapping(entity: FirstPromiseEntity) {
        check(mappings.none { it.draftId == entity.draftId })
        mappings += entity
    }

    override suspend fun insertOutbox(entities: List<FirstPromiseAnalyticsOutboxEntity>) {
        check(entities.none { candidate -> outbox.any { it.draftId == candidate.draftId && it.eventName == candidate.eventName } })
        outbox += entities
        if (failAfterOutboxInsert) error("transaction failure")
    }
}
