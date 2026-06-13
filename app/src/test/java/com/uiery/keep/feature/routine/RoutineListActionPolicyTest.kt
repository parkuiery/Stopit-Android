package com.uiery.keep.feature.routine

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineListActionPolicyTest {
    @Test
    fun runningRoutineSwitchTapReturnsBlockedFeedbackInsteadOfToggle() {
        val action = resolveRoutineEnabledSwitchAction(
            routineId = 609L,
            requestedEnabled = false,
            isBlocked = true,
        )

        assertEquals(RoutineListAction.Blocked, action)
    }

    @Test
    fun editableRoutineSwitchTapReturnsToggleAction() {
        val action = resolveRoutineEnabledSwitchAction(
            routineId = 610L,
            requestedEnabled = false,
            isBlocked = false,
        )

        assertEquals(
            RoutineListAction.ToggleEnabled(routineId = 610L, isEnabled = false),
            action,
        )
    }
}
