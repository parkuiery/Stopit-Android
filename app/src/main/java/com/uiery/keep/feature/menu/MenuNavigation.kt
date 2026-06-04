package com.uiery.keep.feature.menu

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object MenuRoute

fun NavController.navigateToMenu(
    navOptions: NavOptions? = null
) = navigate(route = MenuRoute, navOptions = navOptions)

fun NavGraphBuilder.menuScreen(
    onNavigateDevTool: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateRoutine: () -> Unit,
    onNavigateGoalLockCreation: () -> Unit,
    onNavigateLockHistory: () -> Unit,
    onNavigateEmergencyUnlockSettings: () -> Unit,
) {
    composable<MenuRoute> {
        MenuScreen(
            onNavigateDevTool = onNavigateDevTool,
            onNavigateBack = onNavigateBack,
            onNavigateRoutine = onNavigateRoutine,
            onNavigateGoalLockCreation = onNavigateGoalLockCreation,
            onNavigateLockHistory = onNavigateLockHistory,
            onNavigateEmergencyUnlockSettings = onNavigateEmergencyUnlockSettings,
        )
    }
}
