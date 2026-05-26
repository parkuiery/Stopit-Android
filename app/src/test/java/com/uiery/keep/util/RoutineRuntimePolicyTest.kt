package com.uiery.keep.util

import com.uiery.keep.model.RoutineModel
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineRuntimePolicyTest {
    @Test
    fun shouldBlockPackageReturnsTrueWhenEnabledRoutineTargetsPackageAndIsActive() {
        val activeRoutine = routine(id = 1L, isEnabled = true, lockApplications = listOf("com.example.blocked"))

        assertTrue(
            RoutineRuntimePolicy.shouldBlockPackage(
                packageName = "com.example.blocked",
                routines = listOf(activeRoutine),
                isRoutineActive = { it.id == activeRoutine.id },
            ),
        )
    }

    @Test
    fun shouldBlockPackageReturnsFalseWhenRoutineIsInactive() {
        val inactiveRoutine = routine(id = 2L, isEnabled = true, lockApplications = listOf("com.example.blocked"))

        assertFalse(
            RoutineRuntimePolicy.shouldBlockPackage(
                packageName = "com.example.blocked",
                routines = listOf(inactiveRoutine),
                isRoutineActive = { false },
            ),
        )
    }

    @Test
    fun isAnyRoutineActiveReturnsTrueWhenAnyEnabledRoutineIsActive() {
        val activeRoutine = routine(id = 3L, isEnabled = true)
        val disabledRoutine = routine(id = 4L, isEnabled = false)

        assertTrue(
            RoutineRuntimePolicy.isAnyRoutineActive(
                routines = listOf(disabledRoutine, activeRoutine),
                isRoutineActive = { it.id == activeRoutine.id },
            ),
        )
    }

    private fun routine(
        id: Long,
        isEnabled: Boolean,
        lockApplications: List<String>? = listOf("com.example.other"),
    ) = RoutineModel(
        id = id,
        name = "Routine $id",
        startTime = LocalTime(9, 0),
        endTime = LocalTime(10, 0),
        repeatDays = "1111111",
        lockApplications = lockApplications,
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}
