package com.uiery.keep.feature.lockhistory

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.routine.RepeatBlockRoutineSuggestion
import kotlinx.serialization.Serializable

@Serializable
data object LockHistoryRoute

fun NavController.navigateToLockHistory(
    navOptions: NavOptions? = null,
) = navigate(
    route = LockHistoryRoute,
    navOptions = navOptions,
)

fun NavGraphBuilder.lockHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateBlockedApps: () -> Unit,
    onNavigateRoutineWithRepeatBlockPrefill: (RepeatBlockRoutineSuggestion) -> Unit,
) {
    composable<LockHistoryRoute> {
        LockHistoryScreen(
            onNavigateBack = onNavigateBack,
            onNavigateBlockedApps = onNavigateBlockedApps,
            onNavigateRoutineWithRepeatBlockPrefill = onNavigateRoutineWithRepeatBlockPrefill,
        )
    }
}
