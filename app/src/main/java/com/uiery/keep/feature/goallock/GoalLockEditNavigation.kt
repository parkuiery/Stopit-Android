package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.StateFlow

@Serializable
internal data class GoalLockEditRoute(
    val goalLockId: Long,
)

internal fun NavController.navigateToGoalLockEdit(goalLockId: Long) {
    navigate(
        route = GoalLockEditRoute(goalLockId = goalLockId),
        navOptions = goalLockEditNavOptions(),
    )
}

internal fun goalLockEditNavOptions(): NavOptions = navOptions {
    launchSingleTop = true
}

internal fun NavGraphBuilder.goalLockEditScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
) {
    composable<GoalLockEditRoute> {
        GoalLockEditScreen(
            onNavigateBack = onNavigateBack,
            onSaved = onSaved,
        )
    }
}

internal fun SavedStateHandle.markGoalLockEditSaved() {
    this[GOAL_LOCK_EDIT_SAVED_RESULT] = true
}

internal fun SavedStateHandle.consumeGoalLockEditSaved(): Boolean =
    remove<Boolean>(GOAL_LOCK_EDIT_SAVED_RESULT) == true

internal fun SavedStateHandle.goalLockEditSavedFlow(): StateFlow<Boolean> =
    getStateFlow(GOAL_LOCK_EDIT_SAVED_RESULT, false)

private const val GOAL_LOCK_EDIT_SAVED_RESULT = "goalLockEditSaved"
