package com.uiery.keep.feature.lock.component

import com.uiery.keep.service.EMERGENCY_UNLOCK_REASON_NOT_REQUIRED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockBottomSheetStateTest {

    @Test
    fun reasonEnabledFlowRequiresReasonThenCustomReasonBeforeAppsStep() {
        var state = EmergencyUnlockBottomSheetState.initial(
            blockedApps = setOf("com.social.app"),
            durationOptions = listOf(5, 10),
            reasonStepEnabled = true,
        )

        assertEquals(EmergencyUnlockBottomSheetStep.REASON, state.step)
        assertEquals(listOf(EmergencyUnlockBottomSheetStep.REASON, EmergencyUnlockBottomSheetStep.APPS, EmergencyUnlockBottomSheetStep.DURATION), state.visibleSteps)
        assertFalse(state.canContinueFromReason)

        state = state.selectReason("other")
        assertFalse(state.canContinueFromReason)

        state = state.changeCustomReason("urgent family call")
        assertTrue(state.canContinueFromReason)

        state = state.goNext()
        assertEquals(EmergencyUnlockBottomSheetStep.APPS, state.step)
    }

    @Test
    fun reasonDisabledFlowStartsAtAppsAndCompletesWithNotRequiredReason() {
        var state = EmergencyUnlockBottomSheetState.initial(
            blockedApps = setOf("com.social.app"),
            durationOptions = emptyList(),
            reasonStepEnabled = false,
        )

        assertEquals(EmergencyUnlockBottomSheetStep.APPS, state.step)
        assertEquals(listOf(EmergencyUnlockBottomSheetStep.APPS, EmergencyUnlockBottomSheetStep.DURATION), state.visibleSteps)
        assertEquals(3, state.selectedDurationMinutes)

        state = state.toggleApp("com.social.app").goNext().selectDuration(30).goNext()

        assertEquals(EmergencyUnlockBottomSheetStep.COUNTDOWN, state.step)
        assertEquals(
            EmergencyUnlockBottomSheetRequest(
                reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
                customReason = null,
                apps = setOf("com.social.app"),
                durationMinutes = 30,
            ),
            state.toUnlockRequest(),
        )
    }

    @Test
    fun appStepRequiresSelectionAndIgnoresUnknownPackages() {
        var state = EmergencyUnlockBottomSheetState.initial(
            blockedApps = setOf("com.social.app", "com.game.app"),
            durationOptions = listOf(10),
            reasonStepEnabled = false,
        )

        assertFalse(state.canContinueFromApps)

        state = state.toggleApp("com.unknown.app")
        assertTrue(state.selectedApps.isEmpty())
        assertFalse(state.canContinueFromApps)

        state = state.toggleApp("com.social.app")
        assertEquals(setOf("com.social.app"), state.selectedApps)
        assertTrue(state.canContinueFromApps)

        state = state.toggleApp("com.social.app")
        assertTrue(state.selectedApps.isEmpty())
        assertFalse(state.canContinueFromApps)
    }

    @Test
    fun countdownTicksCompleteAndCancelAreExplicitEffects() {
        var state = EmergencyUnlockBottomSheetState.initial(
            blockedApps = setOf("com.social.app"),
            durationOptions = listOf(10),
            reasonStepEnabled = false,
        ).toggleApp("com.social.app").goNext().goNext()

        assertEquals(EmergencyUnlockBottomSheetStep.COUNTDOWN, state.step)
        assertEquals(30, state.countdownSeconds)

        val tick = state.countdownTick()
        state = tick.state
        assertEquals(EmergencyUnlockBottomSheetEffect.None, tick.effect)
        assertEquals(29, state.countdownSeconds)

        val completed = state.copy(countdownSeconds = 1).countdownTick()
        assertEquals(EmergencyUnlockBottomSheetEffect.SubmitUnlock, completed.effect)
        assertEquals(0, completed.state.countdownSeconds)

        val cancelled = state.cancelCountdown()
        assertEquals(EmergencyUnlockBottomSheetEffect.Dismiss, cancelled.effect)
    }
}
