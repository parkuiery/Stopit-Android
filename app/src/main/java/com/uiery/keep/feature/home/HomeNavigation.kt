package com.uiery.keep.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

fun NavController.navigateToHome(
    navOptions: NavOptions = navOptions {
        popUpTo(graph.id) {
            inclusive = true
        }
    }
) = navigate(route = HomeRoute, navOptions = navOptions)

fun NavGraphBuilder.homeScreen(
    onNavigateMenu: () -> Unit,
    onNavigateLock: (lockTime: String?,Boolean) -> Unit,
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateMenu = onNavigateMenu,
            onNavigateLock = onNavigateLock,
            onNavigateGoalLockDetail = onNavigateGoalLockDetail,
        )
    }
}
