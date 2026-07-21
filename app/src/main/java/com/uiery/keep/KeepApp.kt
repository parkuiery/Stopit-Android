package com.uiery.keep

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.uiery.keep.feature.devtool.devToolScreen
import com.uiery.keep.feature.devtool.navigateToDevTool
import com.uiery.keep.feature.emergencyunlocksettings.emergencyUnlockSettingsScreen
import com.uiery.keep.feature.emergencyunlocksettings.navigateToEmergencyUnlockSettings
import com.uiery.keep.feature.lockhistory.blockedapps.blockedAppsScreen
import com.uiery.keep.feature.lockhistory.blockedapps.navigateToBlockedApps
import com.uiery.keep.feature.goallock.GoalLockCreationRoute
import com.uiery.keep.feature.lockhistory.LockHistoryRoute
import com.uiery.keep.feature.lockhistory.lockHistoryScreen
import com.uiery.keep.feature.lockhistory.navigateToLockHistory
import com.uiery.keep.feature.home.homeScreen
import com.uiery.keep.feature.home.navigateToHome
import com.uiery.keep.feature.goallock.goalLockCreationScreen
import com.uiery.keep.feature.goallock.goalLockDetailScreen
import com.uiery.keep.feature.goallock.goalLockEditScreen
import com.uiery.keep.feature.goallock.markGoalLockEditSaved
import com.uiery.keep.feature.goallock.navigateToGoalLockEdit
import com.uiery.keep.feature.goallock.navigateToGoalLockCreation
import com.uiery.keep.feature.goallock.navigateToGoalLockDetail
import com.uiery.keep.feature.goallock.navigateToGoalLockDetailAfterCreation
import com.uiery.keep.feature.lock.lockScreen
import com.uiery.keep.feature.lock.navigateToLock
import com.uiery.keep.feature.menu.menuScreen
import com.uiery.keep.feature.menu.navigateToMenu
import com.uiery.keep.feature.onboarding.Onboarding
import com.uiery.keep.feature.onboarding.navigateToOnboarding
import com.uiery.keep.feature.onboarding.entry.handleOnboardingEntrySideEffect
import com.uiery.keep.feature.onboarding.notification.navigateToNotificationSetting
import com.uiery.keep.feature.onboarding.notification.navigateToPromiseNotification
import com.uiery.keep.feature.onboarding.onboarding
import com.uiery.keep.feature.onboarding.permission.navigateToPermissionSetting
import com.uiery.keep.feature.onboarding.permission.navigateToPromiseAccessibility
import com.uiery.keep.feature.onboarding.result.navigateToPromiseResult
import com.uiery.keep.feature.onboarding.select.navigateToSelectApp
import com.uiery.keep.feature.onboarding.usageaccess.navigateToUsageAccess
import com.uiery.keep.feature.onboarding.usageanalysis.navigateToPromiseProposal
import com.uiery.keep.feature.onboarding.usageanalysis.navigateToUsageAnalysis
import com.uiery.keep.feature.parentmode.navigateToParentModeSetup
import com.uiery.keep.feature.parentmode.parentModeSetupScreen
import com.uiery.keep.feature.routine.navigateToRoutine
import com.uiery.keep.feature.routine.navigateToRoutineWithRepeatBlockPrefill
import com.uiery.keep.feature.routine.navigateToRoutineWithUsageInsightPrefill
import com.uiery.keep.feature.routine.routineScreen
import com.uiery.keep.feature.splash.SplashRoute
import com.uiery.keep.feature.splash.splashScreen
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface

@Composable
internal fun KeepApp(
    modifier: Modifier = Modifier,
    startDestination: Any = SplashRoute,
    newIntentDestination: Any? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(newIntentDestination) {
        newIntentDestination?.let { destination ->
            navController.navigate(destination) {
                launchSingleTop = true
            }
        }
    }

    val isDevToolEnabled = shouldRegisterDevToolRoute(
        flavor = BuildConfig.FLAVOR,
        isDebug = BuildConfig.DEBUG,
    )

    NavHost(
        modifier = modifier
            .fillMaxSize()
            .testTag("stopit_app_nav_host"),
        navController = navController,
        startDestination = startDestination,
    ) {
        splashScreen(
            onNavigateHome = navController::navigateToHome,
            onNavigateOnboarding = navController::navigateToOnboarding,
            onNavigateLock = navController::navigateToLock,
        )
        onboarding(
            onEntrySideEffect = navController::handleOnboardingEntrySideEffect,
            onNavigatePermissionSetting = navController::navigateToPermissionSetting,
            onNavigateNotificationSetting = navController::navigateToNotificationSetting,
            onNavigateSelectApp = navController::navigateToSelectApp,
            onNavigateHome = navController::navigateToHome,
            onNavigateLock = navController::navigateToLock,
            onNavigateUsageAccess = navController::navigateToUsageAccess,
            onNavigateUsageAnalysis = navController::navigateToUsageAnalysis,
            onNavigateManualAppSelect = { navController.navigate(Onboarding.Route.ManualAppSelect) },
            onNavigateManualPromiseProposal = { navController.navigate(Onboarding.Route.PromiseProposal) },
            onNavigatePromiseProposal = navController::navigateToPromiseProposal,
            onNavigatePromiseAccessibility = navController::navigateToPromiseAccessibility,
            onNavigatePromiseNotification = navController::navigateToPromiseNotification,
            onNavigatePromisePersistence = {
                navController.navigateToPromiseResult()
            },
            onNavigateBackFromPromiseAccessibility = navController::navigateUp,
            onNavigatePromiseEdit = {
                navController.navigate(Onboarding.Route.PromiseProposal) {
                    popUpTo(Onboarding.Route.PromiseProposal) { inclusive = false }
                    launchSingleTop = true
                }
            },
        )
        homeScreen(
            onNavigateMenu = navController::navigateToMenu,
            onNavigateLock = navController::navigateToLock,
            onNavigateLockHistory = navController::navigateToLockHistory,
            onNavigateRoutine = { entrySurface, creationSource ->
                navController.navigateToRoutine(
                    routineSavedEntrySurface = entrySurface,
                    routineSavedCreationSource = creationSource,
                )
            },
            onNavigateGoalLockDetail = navController::navigateToGoalLockDetail,
            onNavigateRoutineWithRepeatBlockPrefill = { suggestion ->
                navController.navigateToRoutineWithRepeatBlockPrefill(
                    surface = RepeatBlockRoutineSuggestionSurface.HOME,
                    suggestion = suggestion,
                )
            },
            onNavigateRoutineWithUsageInsightPrefill = { prefill ->
                navController.navigateToRoutineWithUsageInsightPrefill(prefill)
            },
        )
        menuScreen(
            onNavigateDevTool = if (isDevToolEnabled) {
                { navController.navigateToDevTool() }
            } else {
                {}
            },
            onNavigateBack = navController::navigateUp,
            onNavigateRoutine = navController::navigateToRoutine,
            onNavigateGoalLockCreation = navController::navigateToGoalLockCreation,
            onNavigateGoalLockDetail = navController::navigateToGoalLockDetail,
            onNavigateParentModeSetup = navController::navigateToParentModeSetup,
            onNavigateLockHistory = navController::navigateToLockHistory,
            onNavigateEmergencyUnlockSettings = navController::navigateToEmergencyUnlockSettings,
        )
        lockScreen(onNavigateHome = navController::navigateToHome)
        if (isDevToolEnabled) {
            devToolScreen(onNavigateBack = navController::navigateUp)
        }
        routineScreen(
            onNavigateBack = navController::navigateUp,
            onNavigateLock = navController::navigateToLock,
        )
        lockHistoryScreen(
            onNavigateBack = navController::navigateUp,
            onNavigateBlockedApps = navController::navigateToBlockedApps,
            onNavigateRoutineWithRepeatBlockPrefill = { suggestion ->
                navController.navigateToRoutineWithRepeatBlockPrefill(
                    surface = RepeatBlockRoutineSuggestionSurface.PERFORMANCE_REPORT,
                    suggestion = suggestion,
                )
            },
        )
        blockedAppsScreen(onNavigateBack = navController::navigateUp)
        emergencyUnlockSettingsScreen(onNavigateBack = navController::navigateUp)
        parentModeSetupScreen(onNavigateBack = navController::navigateUp)
        goalLockCreationScreen(
            onNavigateBack = navController::navigateUp,
            onNavigateGoalLockDetail = navController::navigateToGoalLockDetailAfterCreation,
        )
        goalLockDetailScreen(
            onNavigateBack = navController::navigateUp,
            onNavigateEdit = navController::navigateToGoalLockEdit,
        )
        goalLockEditScreen(
            onNavigateBack = navController::navigateUp,
            onSaved = {
                navController.previousBackStackEntry?.savedStateHandle?.markGoalLockEditSaved()
                navController.navigateUp()
            },
        )
    }
}

internal fun shouldRegisterDevToolRoute(
    flavor: String,
    isDebug: Boolean,
): Boolean = flavor == "dev" && isDebug

internal fun canonicalHistoryRoute() = LockHistoryRoute

internal fun shouldRegisterLegacyHistoryRoute(): Boolean = false

internal fun canonicalGoalLockCreationRoute() = GoalLockCreationRoute

internal fun shouldRegisterGoalLockCreationEntryRoute(): Boolean = true
