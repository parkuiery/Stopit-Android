package com.uiery.keep.feature.lock

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.serialization.Serializable

@Serializable
data class LockRoute(val lockTime: String?,val isRoutine:Boolean)

fun NavController.navigateToLock(
    lockTime: String?,
    isRoutine: Boolean,
    navOptions: NavOptions = navOptions {
        popUpTo(graph.id) {
            inclusive = true
        }
    },
) = navigate(
    route = LockRoute(lockTime = lockTime,isRoutine = isRoutine),
    navOptions = navOptions,
)

fun NavGraphBuilder.lockScreen(
    onNavigateHome: () -> Unit,
) {
    composable<LockRoute> {
        LockScreen(
            onNavigateHome = onNavigateHome
        )
    }
}