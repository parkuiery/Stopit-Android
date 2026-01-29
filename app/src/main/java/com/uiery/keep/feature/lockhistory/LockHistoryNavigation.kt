package com.uiery.keep.feature.lockhistory

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LockHistoryRoute

fun NavController.navigateToLockHistory(
    navOptions: NavOptions? = null,
) = navigate(
    route = LockHistoryRoute,
    navOptions = navOptions,
)

fun NavGraphBuilder.lockHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateBlockedApps: () -> Unit,
) {
    composable<LockHistoryRoute> {
        LockHistoryScreen(
            onNavigateBack = onNavigateBack,
            onNavigateBlockedApps = onNavigateBlockedApps,
        )
    }
}
