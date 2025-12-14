package com.uiery.keep.feature.history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute

fun NavController.navigateToHistory(
    navOptions: NavOptions? = null,
) = navigate(
    route = HistoryRoute,
    navOptions = navOptions,
)

fun NavGraphBuilder.historyScreen(
    onNavigateBack: () -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}