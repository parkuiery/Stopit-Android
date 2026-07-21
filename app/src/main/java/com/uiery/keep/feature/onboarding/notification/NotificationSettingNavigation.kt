package com.uiery.keep.feature.onboarding.notification

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding
import com.uiery.keep.feature.onboarding.OnboardingPermissionContext

fun NavController.navigateToNotificationSetting(
    navOptions: NavOptions? = null
) = navigate(
    route = Onboarding.Route.NotificationSetting,
    navOptions = navOptions,
)

fun NavController.navigateToPromiseNotification(navOptions: NavOptions? = null) = navigate(
    route = Onboarding.Route.PromiseNotification,
    navOptions = navOptions,
)

fun NavGraphBuilder.notificationSettingScreen(
    onNavigateSelectApp: () -> Unit,
    onNavigatePromisePersistence: () -> Unit,
) {
    composable<Onboarding.Route.NotificationSetting> {
        NotificationSettingScreen(
            onNavigateSelectApp = onNavigateSelectApp,
        )
    }
    composable<Onboarding.Route.PromiseNotification> {
        NotificationSettingScreen(
            context = OnboardingPermissionContext.FirstPromise,
            onNavigateSelectApp = {},
            onNavigatePersistence = onNavigatePromisePersistence,
        )
    }
}
