package com.uiery.keep.feature.parentmode

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ParentModeSetupRoute

fun NavController.navigateToParentModeSetup(
    navOptions: NavOptions? = null,
) = navigate(route = ParentModeSetupRoute, navOptions = navOptions)

fun NavGraphBuilder.parentModeSetupScreen(
    onNavigateBack: () -> Unit,
) {
    composable<ParentModeSetupRoute> {
        ParentModeSetupScreen(onNavigateBack = onNavigateBack)
    }
}
