package com.uiery.keep.data.firstpromise

import androidx.room.withTransaction
import com.uiery.keep.database.KeepDatabase
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.database.entity.FirstPromiseEntity
import com.uiery.keep.database.mapper.toEntity
import com.uiery.keep.database.mapper.toModel
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.model.RoutineModel
import java.time.Clock
import javax.inject.Inject

data class FirstPromiseCreationResult(
    val routineId: Long,
    val routine: RoutineModel,
    val scheduleState: FirstPromiseScheduleState,
    val schedulingSucceeded: Boolean,
    val created: Boolean,
)

interface FirstPromiseCreator {
    suspend fun createFirstPromise(
        draft: FirstPromiseDraft,
        routine: RoutineModel,
    ): FirstPromiseCreationResult
}

class FirstPromiseRepository : FirstPromiseCreator {
    private val storage: FirstPromiseRepositoryStorage
    private val exactAlarmOrchestrator: RoutineExactAlarmOrchestrator
    private val codec: FirstPromiseOutboxEventCodec
    private val clock: Clock

    @Inject
    constructor(
        database: KeepDatabase,
        exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
        codec: FirstPromiseOutboxEventCodec,
        clock: Clock,
    ) {
        storage = RoomFirstPromiseRepositoryStorage(database)
        this.exactAlarmOrchestrator = exactAlarmOrchestrator
        this.codec = codec
        this.clock = clock
    }

    internal constructor(
        storage: FirstPromiseRepositoryStorage,
        exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
        codec: FirstPromiseOutboxEventCodec,
        clock: Clock,
    ) {
        this.storage = storage
        this.exactAlarmOrchestrator = exactAlarmOrchestrator
        this.codec = codec
        this.clock = clock
    }

    override suspend fun createFirstPromise(
        draft: FirstPromiseDraft,
        routine: RoutineModel,
    ): FirstPromiseCreationResult {
        storage.findMapping(draft.draftId)?.let { return existingResult(it) }

        // Permission resolution must happen before any routine insert. The transaction re-check below
        // remains the authoritative concurrency/idempotency barrier.
        val permissionResolution = exactAlarmOrchestrator.resolveBeforePersist(routine)
        var scheduledRoutineId: Long? = null
        return try {
            storage.inTransaction {
                findMapping(draft.draftId)?.let { return@inTransaction existingResult(it) }

                val routineId = insertRoutine(permissionResolution.routine)
                val routineWithId = permissionResolution.routine.copy(id = routineId)
                val scheduleDecision = if (routineWithId.isEnabled) {
                    exactAlarmOrchestrator.scheduleEnabledRoutine(routineWithId)
                } else {
                    null
                }
                val finalizedRoutine = scheduleDecision?.routine ?: routineWithId
                val schedulingSucceeded = scheduleDecision?.shouldTrackLockScheduled == true
                if (schedulingSucceeded) scheduledRoutineId = routineId
                if (finalizedRoutine != routineWithId) updateRoutine(finalizedRoutine)

                val scheduleState = when {
                    finalizedRoutine.isEnabled && schedulingSucceeded -> FirstPromiseScheduleState.Enabled
                    permissionResolution.shouldShowPermissionPrompt ||
                        scheduleDecision?.shouldShowPermissionPrompt == true ->
                        FirstPromiseScheduleState.DisabledExactAlarmMissing
                    routine.isEnabled -> FirstPromiseScheduleState.DisabledUnknown
                    else -> FirstPromiseScheduleState.DisabledUserChoice
                }
                insertMapping(
                    FirstPromiseEntity(
                        draftId = draft.draftId,
                        routineId = routineId,
                        goalType = draft.goal.analyticsValue,
                        source = draft.source.analyticsValue,
                        createdAtMillis = clock.millis(),
                    ),
                )
                insertOutbox(
                    listOf(
                        codec.encode(
                            draftId = draft.draftId,
                            event = FirstPromiseOutboxEvent.RoutineSaved(
                                repeatDaysBucket = repeatDaysBucket(draft.repeatDays.size),
                                timeWindowBucket = timeWindowBucket(draft.startMinutes),
                                scheduleState = scheduleState,
                            ),
                            occurredAtMillis = clock.millis(),
                        ),
                        codec.encode(
                            draftId = draft.draftId,
                            event = FirstPromiseOutboxEvent.FirstPromiseCreated(
                                goal = draft.goal,
                                source = draft.source,
                                scheduleState = scheduleState,
                            ),
                            occurredAtMillis = clock.millis(),
                        ),
                    ),
                )
                FirstPromiseCreationResult(
                    routineId = routineId,
                    routine = finalizedRoutine,
                    scheduleState = scheduleState,
                    schedulingSucceeded = schedulingSucceeded,
                    created = true,
                )
            }
        } catch (failure: Throwable) {
            scheduledRoutineId?.let(exactAlarmOrchestrator::cancelRoutine)
            throw failure
        }
    }

    private suspend fun existingResult(mapping: FirstPromiseEntity): FirstPromiseCreationResult {
        val routine = storage.findRoutine(mapping.routineId)
        val creationEvent = storage.findOutbox(mapping.draftId)
            .firstOrNull { it.sequence == 20 }
            ?.let(codec::decodeOrNull) as? FirstPromiseOutboxEvent.FirstPromiseCreated
        val state = creationEvent?.scheduleState ?: if (routine.isEnabled) {
            FirstPromiseScheduleState.Enabled
        } else {
            FirstPromiseScheduleState.DisabledUnknown
        }
        return FirstPromiseCreationResult(
            routineId = mapping.routineId,
            routine = routine,
            scheduleState = state,
            schedulingSucceeded = state == FirstPromiseScheduleState.Enabled,
            created = false,
        )
    }
}

internal interface FirstPromiseRepositoryStorage {
    suspend fun findMapping(draftId: String): FirstPromiseEntity?
    suspend fun findRoutine(id: Long): RoutineModel
    suspend fun findOutbox(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> = emptyList()
    suspend fun <T> inTransaction(block: suspend FirstPromiseRepositoryStorage.() -> T): T
    suspend fun insertRoutine(routine: RoutineModel): Long
    suspend fun updateRoutine(routine: RoutineModel)
    suspend fun insertMapping(entity: FirstPromiseEntity)
    suspend fun insertOutbox(entities: List<FirstPromiseAnalyticsOutboxEntity>)
}

private class RoomFirstPromiseRepositoryStorage(
    private val database: KeepDatabase,
) : FirstPromiseRepositoryStorage {
    override suspend fun findMapping(draftId: String): FirstPromiseEntity? =
        database.firstPromiseDao().findByDraftId(draftId)

    override suspend fun findRoutine(id: Long): RoutineModel = database.routineDao().fetch(id).toModel()

    override suspend fun findOutbox(draftId: String): List<FirstPromiseAnalyticsOutboxEntity> =
        database.firstPromiseAnalyticsOutboxDao().findByDraftId(draftId)

    override suspend fun <T> inTransaction(block: suspend FirstPromiseRepositoryStorage.() -> T): T =
        database.withTransaction { block(this@RoomFirstPromiseRepositoryStorage) }

    override suspend fun insertRoutine(routine: RoutineModel): Long = database.routineDao().insert(routine.toEntity())

    override suspend fun updateRoutine(routine: RoutineModel) {
        database.routineDao().update(routine.toEntity())
    }

    override suspend fun insertMapping(entity: FirstPromiseEntity) {
        database.firstPromiseDao().insert(entity)
    }

    override suspend fun insertOutbox(entities: List<FirstPromiseAnalyticsOutboxEntity>) {
        database.firstPromiseAnalyticsOutboxDao().insertAll(entities)
    }
}

private fun repeatDaysBucket(size: Int): FirstPromiseRepeatDaysBucket = when (size) {
    0, 1 -> FirstPromiseRepeatDaysBucket.One
    in 2..3 -> FirstPromiseRepeatDaysBucket.TwoThree
    in 4..6 -> FirstPromiseRepeatDaysBucket.FourSix
    else -> FirstPromiseRepeatDaysBucket.Seven
}

private fun timeWindowBucket(startMinutes: Int): FirstPromiseTimeWindowBucket = when (startMinutes / 60) {
    in 5..11 -> FirstPromiseTimeWindowBucket.Morning
    in 12..16 -> FirstPromiseTimeWindowBucket.Afternoon
    in 17..20 -> FirstPromiseTimeWindowBucket.Evening
    else -> FirstPromiseTimeWindowBucket.Night
}
