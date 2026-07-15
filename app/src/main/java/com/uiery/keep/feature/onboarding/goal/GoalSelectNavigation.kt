package com.uiery.keep.feature.onboarding.goal

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

fun NavController.navigateToGoalSelect(navOptions: NavOptions? = null) =
    navigate(Onboarding.Route.GoalSelect, navOptions)

fun NavGraphBuilder.goalSelectScreen(
    onNavigateUsageAccess: () -> Unit,
    onNavigateManualAppSelect: () -> Unit,
) {
    composable<Onboarding.Route.GoalSelect> {
        GoalSelectScreen(onNavigateUsageAccess, onNavigateManualAppSelect)
    }
}
