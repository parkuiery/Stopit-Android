package com.uiery.keep.feature.goallock

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalLockEditScreenPolicyTest {
    @Test
    fun systemAndTopBarBackShareRequestBackAction() {
        assertEquals(
            GoalLockEditBackAction.RequestBack,
            goalLockEditBackAction(GoalLockEditBackSource.System),
        )
        assertEquals(
            GoalLockEditBackAction.RequestBack,
            goalLockEditBackAction(GoalLockEditBackSource.TopBar),
        )
    }
}
