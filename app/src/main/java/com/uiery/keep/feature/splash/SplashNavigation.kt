package com.uiery.keep.feature.splash

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

fun NavController.navigateToSplash(
    navOptions: NavOptions? = null,
) = navigate(route = SplashRoute, navOptions = navOptions)

fun NavGraphBuilder.splashScreen(
    onNavigateHome:() -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigateLock: (lockTime: String?,Boolean) -> Unit,
) {
    composable<SplashRoute> {
        SplashScreen(
            onNavigateHome = onNavigateHome,
            onNavigateOnboarding = onNavigateOnboarding,
            onNavigateLock = onNavigateLock,
        )
    }
}