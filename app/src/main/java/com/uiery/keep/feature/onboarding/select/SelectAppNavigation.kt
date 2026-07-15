package com.uiery.keep.feature.onboarding.select

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding
import com.uiery.keep.ui.component.AppSelectionMode

fun NavController.navigateToSelectApp(
    navOptions: NavOptions? = null
) = navigate(
    route = Onboarding.Route.SelectedApp,
    navOptions = navOptions,
)

fun NavGraphBuilder.selectApp(
    onNavigateHome: () -> Unit,
    onNavigateProposal: () -> Unit,
) {
    composable<Onboarding.Route.SelectedApp> {
        SelectAppScreen(
            onNavigateHome = onNavigateHome,
        )
    }
    composable<Onboarding.Route.ManualAppSelect> {
        SelectAppScreen(
            onNavigateHome = onNavigateHome,
            selectionMode = AppSelectionMode.Single,
            onNavigateProposal = onNavigateProposal,
        )
    }
}
