package com.uiery.keep.feature.emergencyunlocksettings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object EmergencyUnlockSettingsRoute

fun NavController.navigateToEmergencyUnlockSettings(
    navOptions: NavOptions? = null,
) = navigate(route = EmergencyUnlockSettingsRoute, navOptions = navOptions)

fun NavGraphBuilder.emergencyUnlockSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<EmergencyUnlockSettingsRoute> {
        EmergencyUnlockSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
