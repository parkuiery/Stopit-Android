package com.uiery.keep.feature.goallock

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.serialization.Serializable

@Serializable
internal data class GoalLockDetailRoute(
    val goalLockId: Long,
)

internal fun NavController.navigateToGoalLockDetail(goalLockId: Long) =
    navigate(route = GoalLockDetailRoute(goalLockId = goalLockId))

internal fun NavController.navigateToGoalLockDetailAfterCreation(goalLockId: Long) =
    navigate(
        route = GoalLockDetailRoute(goalLockId = goalLockId),
        navOptions = goalLockDetailAfterCreationNavOptions(),
    )

internal fun goalLockDetailAfterCreationNavOptions(): NavOptions = navOptions {
    popUpTo<GoalLockCreationRoute> {
        inclusive = true
    }
}

internal fun NavGraphBuilder.goalLockDetailScreen(
    onNavigateBack: () -> Unit,
) {
    composable<GoalLockDetailRoute> {
        GoalLockDetailScreen(onNavigateBack = onNavigateBack)
    }
}
