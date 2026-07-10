package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

@Serializable
internal data class GoalLockEditRoute(
    val goalLockId: Long,
)

internal fun NavController.navigateToGoalLockEdit(goalLockId: Long) {
    navigate(route = GoalLockEditRoute(goalLockId = goalLockId))
}

internal fun SavedStateHandle.markGoalLockEditSaved() {
    this[GOAL_LOCK_EDIT_SAVED_RESULT] = true
}

internal fun SavedStateHandle.consumeGoalLockEditSaved(): Boolean =
    remove<Boolean>(GOAL_LOCK_EDIT_SAVED_RESULT) == true

private const val GOAL_LOCK_EDIT_SAVED_RESULT = "goalLockEditSaved"
