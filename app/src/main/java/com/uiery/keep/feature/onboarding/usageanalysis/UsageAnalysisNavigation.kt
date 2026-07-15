package com.uiery.keep.feature.onboarding.usageanalysis

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

fun NavController.navigateToUsageAnalysis(navOptions: NavOptions? = null) = navigate(Onboarding.Route.UsageAnalysis, navOptions)

fun NavController.navigateToPromiseProposal() {
    navigate(Onboarding.Route.PromiseProposal)
}

fun NavGraphBuilder.usageAnalysisScreen(
    onNavigateProposal: (Long) -> Unit,
    onNavigateManualAppSelect: () -> Unit,
) {
    composable<Onboarding.Route.UsageAnalysis> {
        UsageAnalysisScreen(onNavigateProposal, onNavigateManualAppSelect)
    }
}
