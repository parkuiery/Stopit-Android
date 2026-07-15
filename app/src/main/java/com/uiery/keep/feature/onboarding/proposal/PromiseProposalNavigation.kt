package com.uiery.keep.feature.onboarding.proposal

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

fun NavController.navigateToPromiseProposal(navOptions: NavOptions? = null) =
    navigate(Onboarding.Route.PromiseProposal, navOptions)

fun NavGraphBuilder.promiseProposalScreen(
    onNavigateAccessibility: () -> Unit,
) {
    composable<Onboarding.Route.PromiseProposal> {
        PromiseProposalScreen(onNavigateAccessibility = onNavigateAccessibility)
    }
}
