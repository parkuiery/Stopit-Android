package com.uiery.keep.feature.routine

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
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
}

private class FakeRoutineDao(
    private val routines: List<RoutineEntity>,
) : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = flowOf(routines)
    override fun fetchAllOnce(): List<RoutineEntity> = routines
    override fun fetch(id: Long): RoutineEntity = routines.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}
