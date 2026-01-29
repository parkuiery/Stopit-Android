package com.uiery.keep.feature.lockhistory.blockedapps

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object BlockedAppsRoute

fun NavController.navigateToBlockedApps(navOptions: NavOptions? = null) =
    navigate(route = BlockedAppsRoute, navOptions = navOptions)

fun NavGraphBuilder.blockedAppsScreen(onNavigateBack: () -> Unit) {
    composable<BlockedAppsRoute> {
        BlockedAppsScreen(onNavigateBack = onNavigateBack)
    }
}
