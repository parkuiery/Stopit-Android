package com.uiery.keep.feature.goallock

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object GoalLockCreationRoute

fun NavController.navigateToGoalLockCreation(
    navOptions: NavOptions? = null,
) = navigate(route = GoalLockCreationRoute, navOptions = navOptions)

fun NavGraphBuilder.goalLockCreationScreen(
    onNavigateBack: () -> Unit,
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit,
) {
    composable<GoalLockCreationRoute> {
        GoalLockCreationScreen(
            onNavigateBack = onNavigateBack,
            onNavigateGoalLockDetail = onNavigateGoalLockDetail,
        )
    }
}
