package com.uiery.keep.feature.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.navigation
import com.uiery.keep.feature.onboarding.entry.OnboardingEntrySideEffect
import com.uiery.keep.feature.onboarding.entry.onboardingEntry
import com.uiery.keep.feature.onboarding.goal.goalSelectScreen
import com.uiery.keep.feature.onboarding.intro.introScreen
import com.uiery.keep.feature.onboarding.notification.notificationSettingScreen
import com.uiery.keep.feature.onboarding.permission.permissionSettingScreen
import com.uiery.keep.feature.onboarding.select.selectApp
import com.uiery.keep.feature.onboarding.usageaccess.usageAccessScreen
import com.uiery.keep.feature.onboarding.usageanalysis.usageAnalysisScreen
import com.uiery.keep.feature.onboarding.usageanalysis.TransientAnalysisProposal
import kotlinx.serialization.Serializable

@Serializable
sealed interface Onboarding {
    @Serializable
    data object Route {

        @Serializable
        data object Entry : Onboarding

        @Serializable
        data object Intro : Onboarding

        @Serializable
        data object PermissionSetting : Onboarding

        @Serializable
        data object NotificationSetting : Onboarding

        @Serializable
        data object SelectedApp : Onboarding

        @Serializable
        data object GoalSelect : Onboarding

        @Serializable
        data object UsageAccess : Onboarding

        @Serializable
        data object UsageAnalysis : Onboarding

        @Serializable
        data object ManualAppSelect : Onboarding

        @Serializable
        data object PromiseProposal : Onboarding

        @Serializable
        data object PromiseAccessibility : Onboarding

        @Serializable
        data object PromiseNotification : Onboarding

        @Serializable
        data object PromiseResult : Onboarding
    }
}

fun NavController.navigateToOnboarding(
    route: Onboarding = defaultOnboardingLaunchDestination(),
    navOptions: NavOptions = navOptions {
        popUpTo(graph.id) {
            inclusive = true
        }
    },
) = navigate(route = route, navOptions = navOptions)

fun NavGraphBuilder.onboarding(
    onEntrySideEffect: (OnboardingEntrySideEffect) -> Unit,
    onNavigatePermissionSetting: () -> Unit,
    onNavigateNotificationSetting: () -> Unit,
    onNavigateSelectApp: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateUsageAccess: () -> Unit,
    onNavigateUsageAnalysis: () -> Unit,
    onNavigateManualAppSelect: () -> Unit,
    onNavigateManualPromiseProposal: () -> Unit,
    onNavigatePromiseProposal: (TransientAnalysisProposal) -> Unit,
) {
    navigation<Onboarding.Route>(
        startDestination = canonicalOnboardingStartDestination()
    ) {
        onboardingEntry(onSideEffect = onEntrySideEffect)
        introScreen(onNavigatePermissionSetting = onNavigatePermissionSetting)
        permissionSettingScreen(onNavigateNotificationSetting = onNavigateNotificationSetting)
        notificationSettingScreen(onNavigateSelectApp = onNavigateSelectApp)
        selectApp(
            onNavigateHome = onNavigateHome,
            onNavigateProposal = onNavigateManualPromiseProposal,
        )
        goalSelectScreen(onNavigateUsageAccess, onNavigateManualAppSelect)
        usageAccessScreen(onNavigateUsageAnalysis, onNavigateManualAppSelect)
        usageAnalysisScreen(onNavigatePromiseProposal, onNavigateManualAppSelect)
    }
}

internal fun canonicalOnboardingStartDestination(): Onboarding = Onboarding.Route.Entry

internal fun defaultOnboardingLaunchDestination(): Onboarding = Onboarding.Route.Entry
