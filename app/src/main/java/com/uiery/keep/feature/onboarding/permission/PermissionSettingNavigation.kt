package com.uiery.keep.feature.onboarding.permission

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding
import com.uiery.keep.feature.onboarding.OnboardingPermissionContext

fun NavController.navigateToPermissionSetting(
    navOptions: NavOptions? = null,
) = navigate(
    route = Onboarding.Route.PermissionSetting,
    navOptions = navOptions,
)

fun NavController.navigateToPromiseAccessibility(navOptions: NavOptions? = null) = navigate(
    route = Onboarding.Route.PromiseAccessibility,
    navOptions = navOptions,
)

fun NavGraphBuilder.permissionSettingScreen(
    onNavigateNotificationSetting: () -> Unit,
    onNavigatePromiseNotification: () -> Unit,
    onNavigatePromiseProposal: () -> Unit,
) {
    composable<Onboarding.Route.PermissionSetting> {
        PermissionSettingScreen(
            onNavigateNotificationSetting = onNavigateNotificationSetting,
        )
    }
    composable<Onboarding.Route.PromiseAccessibility> {
        PermissionSettingScreen(
            flowContext = OnboardingPermissionContext.FirstPromise,
            onNavigateNotificationSetting = onNavigatePromiseNotification,
            onNavigateBack = onNavigatePromiseProposal,
        )
    }
}
