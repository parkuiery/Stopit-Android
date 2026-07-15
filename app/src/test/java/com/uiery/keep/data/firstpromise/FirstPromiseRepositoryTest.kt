package com.uiery.keep.data.firstpromise

import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.database.entity.FirstPromiseEntity
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    fun sameDraftAndConcurrentDuplicateTapsCommitOneRoutineMappingAndOrderedPair() = runBlocking {
        val store = FakeAtomicStore()
        val scheduler = scheduler(canSchedule = true, scheduleResult = RoutineScheduleResult.Scheduled)
        val repository = repository(store, scheduler)

        val results = List(8) { async { repository.createFirstPromise(draft, routine) } }.awaitAll()

        assertEquals(1, results.map { it.routineId }.distinct().size)
        assertEquals(1, store.routines.size)
        assertEquals(1, store.mappings.size)
        assertEquals(listOf(10, 20), store.outbox.map { it.sequence }.sorted())
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
    ): RoutineScheduler = Mockito.mock(RoutineScheduler::class.java).also { scheduler ->
        Mockito.`when`(scheduler.canScheduleExactAlarms()).thenAnswer {
            onCanSchedule()
            canSchedule
        }
        Mockito.`when`(
            scheduler.scheduleRoutine(Mockito.any(RoutineModel::class.java) ?: routine),
        ).thenReturn(scheduleResult)
    }
}

private class FakeAtomicStore(
    private val onInsertRoutine: () -> Unit = {},
    private val failAfterOutboxInsert: Boolean = false,
) : FirstPromiseRepositoryStorage {
    private val mutex = Mutex()
    val routines = mutableListOf<RoutineModel>()
    val mappings = mutableListOf<FirstPromiseEntity>()
    val outbox = mutableListOf<FirstPromiseAnalyticsOutboxEntity>()

    override suspend fun findMapping(draftId: String): FirstPromiseEntity? =
        mappings.firstOrNull { it.draftId == draftId }

    override suspend fun findRoutine(id: Long): RoutineModel =
        routines.first { it.id == id }

    override suspend fun findOutbox(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> =
        outbox.filter { it.draftId == draftId }

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
