package com.uiery.keep.feature.goallock

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
internal data class GoalLockDetailRoute(
    val goalLockId: Long,
)

internal fun NavController.navigateToGoalLockDetail(goalLockId: Long) =
    navigate(route = GoalLockDetailRoute(goalLockId = goalLockId))

internal fun NavGraphBuilder.goalLockDetailScreen(
    onNavigateBack: () -> Unit,
) {
    composable<GoalLockDetailRoute> {
        GoalLockDetailScreen(onNavigateBack = onNavigateBack)
    }
}
