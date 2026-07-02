package com.uiery.keep.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek

class RoutineIdentifierPolicyTest {

    @Test
    fun alarmRequestCodeDoesNotCollapseOverflowAdjacentRoutineIds() {
        val mondayForMaxId = RoutineIdentifierPolicy.alarmRequestCode(
            routineId = Int.MAX_VALUE.toLong() + 1L,
            dayOfWeek = DayOfWeek.MONDAY,
        )
        val mondayForMinIntEquivalentId = RoutineIdentifierPolicy.alarmRequestCode(
            routineId = Int.MIN_VALUE.toLong(),
            dayOfWeek = DayOfWeek.MONDAY,
        )

        assertNotEquals(mondayForMaxId, mondayForMinIntEquivalentId)
    }

    @Test
    fun alarmRequestCodeSeparatesRepeatDaysForLargeRoutineIds() {
        val routineId = Long.MAX_VALUE - 3L

        val requestCodes = DayOfWeek.entries.map { dayOfWeek ->
            RoutineIdentifierPolicy.alarmRequestCode(routineId, dayOfWeek)
        }

        assertEquals(DayOfWeek.entries.size, requestCodes.toSet().size)
    }

    @Test
    fun alarmIntentDataKeepsPendingIntentIdentityUniqueWhenIntRequestCodesWouldOtherwiseCollide() {
        val mondayForMaxId = RoutineIdentifierPolicy.alarmIntentDataValue(
            routineId = Int.MAX_VALUE.toLong() + 1L,
            dayOfWeek = DayOfWeek.MONDAY,
        )
        val mondayForMinIntEquivalentId = RoutineIdentifierPolicy.alarmIntentDataValue(
            routineId = Int.MIN_VALUE.toLong(),
            dayOfWeek = DayOfWeek.MONDAY,
        )

        assertNotEquals(mondayForMaxId, mondayForMinIntEquivalentId)
    }

    @Test
    fun routineNotificationIdUsesSeparateNamespaceFromAlarmRequestCodes() {
        val routineId = 42L

        val notificationId = RoutineIdentifierPolicy.routineStartNotificationId(routineId)
        val alarmRequestCodes = DayOfWeek.entries.map { dayOfWeek ->
            RoutineIdentifierPolicy.alarmRequestCode(routineId, dayOfWeek)
        }

        alarmRequestCodes.forEach { requestCode ->
            assertNotEquals(notificationId, requestCode)
        }
    }

    @Test
    fun legacyAlarmRequestCodeMatchesPreviousFormulaForTransitionCleanup() {
        val routineId = Int.MAX_VALUE.toLong() + 1L
        val dayOfWeek = DayOfWeek.FRIDAY

        assertEquals(
            (routineId * 10 + dayOfWeek.ordinal).toInt(),
            RoutineIdentifierPolicy.legacyAlarmRequestCode(routineId, dayOfWeek),
        )
    }
}
