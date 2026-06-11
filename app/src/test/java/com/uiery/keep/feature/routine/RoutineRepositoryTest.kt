package com.uiery.keep.feature.routine

import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import java.time.DayOfWeek

class RoutineRepositoryTest {
    @Test
    fun fetchAllMapsRoomEntitiesToRoutineModels() = runBlocking {
        val repository = RoomRoutineRepository(
            FakeRoutineDao(
                routines = listOf(
                    RoutineEntity(
                        id = 42,
                        name = "집중 루틴",
                        startTime = LocalTime(9, 0),
                        endTime = LocalTime(18, 0),
                        repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        lockApplications = listOf("com.example.social"),
                        isEnabled = true,
                        changeLockHours = 2,
                    ),
                ),
            ),
        )

        val routines = repository.fetchAll().first()

        assertEquals(1, routines.size)
        assertEquals(42, routines.single().id)
        assertEquals("집중 루틴", routines.single().name)
        assertEquals("1010000", routines.single().repeatDays)
        assertEquals(listOf("com.example.social"), routines.single().lockApplications)
        assertEquals(true, routines.single().isEnabled)
        assertEquals(2, routines.single().changeLockHours)
    }

    @Test
    fun mutationMethodsDelegateThroughRoomDaoMappingBoundary() = runBlocking {
        val dao = FakeRoutineDao(routines = listOf(routineEntity(id = 7L, isEnabled = true)))
        val repository = RoomRoutineRepository(dao)
        val newRoutine = routineModel(id = 0L, isEnabled = true)
        val updatedRoutine = routineModel(id = 7L, isEnabled = false)

        val insertedId = repository.insert(newRoutine)
        val fetched = repository.fetch(7L)
        val fetchedOnce = repository.fetchAllOnce()
        repository.update(updatedRoutine)
        repository.updateIsEnabledById(7L, false)
        repository.deleteById(7L)

        assertEquals(99L, insertedId)
        assertEquals("Routine 7", fetched.name)
        assertEquals(listOf("Routine 7"), fetchedOnce.map { it.name })
        assertEquals("Routine 0", dao.insertedEntity?.name)
        assertEquals("Routine 7", dao.updatedEntity?.name)
        assertFalse(dao.updatedEntity?.isEnabled ?: true)
        assertEquals(7L to false, dao.updatedEnabledCalls.single())
        assertEquals(7L, dao.deletedIds.single())
    }

    private fun routineModel(
        id: Long,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = id,
        name = "Routine $id",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(18, 0),
        repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).toRepeatDaysBinary(),
        lockApplications = listOf("com.example.social"),
        isEnabled = isEnabled,
        changeLockHours = 2,
    )

    private fun routineEntity(
        id: Long,
        isEnabled: Boolean,
    ) = RoutineEntity(
        id = id,
        name = "Routine $id",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(18, 0),
        repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        lockApplications = listOf("com.example.social"),
        isEnabled = isEnabled,
        changeLockHours = 2,
    )
}

private class FakeRoutineDao(
    private val routines: List<RoutineEntity>,
) : RoutineDao {
    var insertedEntity: RoutineEntity? = null
    var updatedEntity: RoutineEntity? = null
    val updatedEnabledCalls = mutableListOf<Pair<Long, Boolean>>()
    val deletedIds = mutableListOf<Long>()

    override fun fetchAll(): Flow<List<RoutineEntity>> = flowOf(routines)
    override fun fetchAllOnce(): List<RoutineEntity> = routines
    override fun fetch(id: Long): RoutineEntity = routines.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long {
        insertedEntity = routineEntity.copy(id = 99L)
        return 99L
    }
    override fun deleteById(id: Long) {
        deletedIds += id
    }
    override fun update(routineEntity: RoutineEntity) {
        updatedEntity = routineEntity
    }
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        updatedEnabledCalls += id to isEnabled
    }
}
