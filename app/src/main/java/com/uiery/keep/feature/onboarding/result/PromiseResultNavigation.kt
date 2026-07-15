package com.uiery.keep.feature.onboarding.result

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

fun NavController.navigateToPromiseResult(navOptions: NavOptions? = null) =
    navigate(Onboarding.Route.PromiseResult, navOptions)

fun NavGraphBuilder.promiseResultScreen(
    onNavigateProposal: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    composable<Onboarding.Route.PromiseResult> {
        PromiseResultScreen(
            onNavigateProposal = onNavigateProposal,
            onNavigateHome = onNavigateHome,
        )
    }
}
