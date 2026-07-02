package com.uiery.keep.feature.routine

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineAlarmPermissionPromptRecoveryTest {
    @Test
    fun promptFlagIsPersistedOnlyWhenUserExplicitlyRequestsSettings() {
        assertFalse(RoutineAlarmPermissionPromptPolicy.shouldPersistShownFlag(RoutineAlarmPermissionPromptAction.SheetShown))
        assertFalse(RoutineAlarmPermissionPromptPolicy.shouldPersistShownFlag(RoutineAlarmPermissionPromptAction.Dismissed))
        assertTrue(RoutineAlarmPermissionPromptPolicy.shouldPersistShownFlag(RoutineAlarmPermissionPromptAction.SettingsRequested))
    }

    @Test
    fun settingsLauncherFallsBackToAppDetailsWhenExactAlarmSettingsCannotOpen() {
        val launchedTargets = mutableListOf("exact")
        val result = RoutineAlarmPermissionSettingsLauncher.open(
            exactAlarmTarget = "exact",
            appDetailsTarget = "app-details",
            launch = { target ->
                if (target == "exact") {
                    throw SecurityException("settings blocked")
                }
                launchedTargets += target
            },
        )

        assertEquals(RoutineAlarmPermissionSettingsLaunchResult.AppDetailsFallbackOpened, result)
        assertEquals(listOf("exact", "app-details"), launchedTargets)
    }

    @Test
    fun settingsLauncherReturnsUnavailableWhenBothExactAndFallbackCannotOpen() {
        val launchedTargets = mutableListOf<String>()
        val result = RoutineAlarmPermissionSettingsLauncher.open(
            exactAlarmTarget = "exact",
            appDetailsTarget = "app-details",
            launch = { target ->
                launchedTargets += target
                throw ActivityNotFoundException("no activity")
            },
        )

        assertEquals(RoutineAlarmPermissionSettingsLaunchResult.Unavailable, result)
        assertEquals(listOf("exact", "app-details"), launchedTargets)
    }
}
