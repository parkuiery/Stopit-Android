package com.uiery.keep.feature.routine

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object RoutineRoute

fun NavController.navigateToRoutine(
    navOptions: NavOptions? = null
) = navigate(
    route = RoutineRoute,
    navOptions = navOptions,
)

fun NavGraphBuilder.routineScreen(
    onNavigateBack: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
) {
    composable<RoutineRoute> {
        RoutineScreen(
            onNavigateBack = onNavigateBack,
            onNavigateLock = onNavigateLock,
        )
    }
}