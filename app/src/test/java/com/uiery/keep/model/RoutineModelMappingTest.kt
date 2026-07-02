package com.uiery.keep.model

import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.database.mapper.toEntity
import com.uiery.keep.database.mapper.toModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class RoutineModelMappingTest {

    @Test
    fun entityToModelConvertsRepeatDaysToBinaryString() {
        val entity = RoutineEntity(
            id = 7,
            name = "Morning focus",
            startTime = LocalTime(hour = 9, minute = 0),
            endTime = LocalTime(hour = 10, minute = 30),
            repeatDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            lockApplications = listOf("com.chat", "com.video"),
            isEnabled = true,
            changeLockHours = 2,
        )

        val model = entity.toModel()

        assertEquals(7, model.id)
        assertEquals("Morning focus", model.name)
        assertEquals(LocalTime(hour = 9, minute = 0), model.startTime)
        assertEquals(LocalTime(hour = 10, minute = 30), model.endTime)
        assertEquals("1010100", model.repeatDays)
        assertEquals(listOf("com.chat", "com.video"), model.lockApplications)
        assertEquals(true, model.isEnabled)
        assertEquals(2, model.changeLockHours)
    }

    @Test
    fun modelToEntityConvertsNullLockedApplicationsToEmptyList() {
        val model = RoutineModel(
            id = 9,
            name = "Evening focus",
            startTime = LocalTime(hour = 21, minute = 0),
            endTime = LocalTime(hour = 22, minute = 0),
            repeatDays = "0100010",
            lockApplications = null,
            isEnabled = false,
            changeLockHours = null,
        )

        val entity = model.toEntity()

        assertEquals(9, entity.id)
        assertEquals("Evening focus", entity.name)
        assertEquals(LocalTime(hour = 21, minute = 0), entity.startTime)
        assertEquals(LocalTime(hour = 22, minute = 0), entity.endTime)
        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), entity.repeatDays)
        assertEquals(emptyList<String>(), entity.lockApplications)
        assertEquals(false, entity.isEnabled)
        assertEquals(null, entity.changeLockHours)
    }
}
