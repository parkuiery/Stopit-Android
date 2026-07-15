package com.uiery.keep.feature.onboarding.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.uiery.keep.feature.home.HomeRoute
import com.uiery.keep.feature.onboarding.Onboarding
import org.orbitmvi.orbit.compose.collectSideEffect

internal fun NavController.handleOnboardingEntrySideEffect(effect: OnboardingEntrySideEffect) {
    if (effect !is OnboardingEntrySideEffect.Navigate) return
    navigate(
        route = effect.destination.route(),
        navOptions = onboardingEntryNavOptions(),
    )
}

internal fun onboardingEntryNavOptions(): NavOptions = navOptions {
    popUpTo<Onboarding.Route.Entry> { inclusive = true }
}

internal fun NavGraphBuilder.onboardingEntry(
    onSideEffect: (OnboardingEntrySideEffect) -> Unit,
) {
    composable<Onboarding.Route.Entry> {
        OnboardingEntryRoute(onSideEffect = onSideEffect)
    }
}

@Composable
private fun OnboardingEntryRoute(
    viewModel: OnboardingEntryViewModel = hiltViewModel(),
    onSideEffect: (OnboardingEntrySideEffect) -> Unit,
) {
    viewModel.collectSideEffect(sideEffect = onSideEffect)
    LaunchedEffect(viewModel) {
        viewModel.resolve()
    }
}

private fun OnboardingEntryDestination.route(): Any = when (this) {
    OnboardingEntryDestination.Intro -> Onboarding.Route.Intro
    OnboardingEntryDestination.GoalSelect -> Onboarding.Route.GoalSelect
    OnboardingEntryDestination.UsageAccess -> Onboarding.Route.UsageAccess
    OnboardingEntryDestination.UsageAnalysis -> Onboarding.Route.UsageAnalysis
    OnboardingEntryDestination.ManualAppSelect -> Onboarding.Route.ManualAppSelect
    OnboardingEntryDestination.PromiseProposal -> Onboarding.Route.PromiseProposal
    OnboardingEntryDestination.PromiseAccessibility -> Onboarding.Route.PromiseAccessibility
    OnboardingEntryDestination.PromiseNotification -> Onboarding.Route.PromiseNotification
    OnboardingEntryDestination.PromiseResult -> Onboarding.Route.PromiseResult
    OnboardingEntryDestination.Home -> HomeRoute
}
