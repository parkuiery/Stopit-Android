package com.uiery.keep.feature.onboarding.usageaccess

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

fun NavController.navigateToUsageAccess(navOptions: NavOptions? = null) = navigate(Onboarding.Route.UsageAccess, navOptions)

fun NavGraphBuilder.usageAccessScreen(
    onNavigateUsageAnalysis: () -> Unit,
    onNavigateManualAppSelect: () -> Unit,
) {
    composable<Onboarding.Route.UsageAccess> {
        UsageAccessScreen(onNavigateUsageAnalysis, onNavigateManualAppSelect)
    }
}
