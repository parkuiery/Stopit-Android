package com.uiery.keep.feature.routine

import android.content.ActivityNotFoundException

enum class RoutineAlarmPermissionPromptAction {
    SheetShown,
    Dismissed,
    SettingsRequested,
}

object RoutineAlarmPermissionPromptPolicy {
    fun shouldPersistShownFlag(action: RoutineAlarmPermissionPromptAction): Boolean =
        action == RoutineAlarmPermissionPromptAction.SettingsRequested
}

enum class RoutineAlarmPermissionSettingsLaunchResult {
    ExactAlarmSettingsOpened,
    AppDetailsFallbackOpened,
    Unavailable,
}

object RoutineAlarmPermissionSettingsLauncher {
    fun <T> open(
        exactAlarmTarget: T?,
        appDetailsTarget: T,
        launch: (T) -> Unit,
    ): RoutineAlarmPermissionSettingsLaunchResult {
        if (exactAlarmTarget != null) {
            val exactResult = runCatchingRecoverable {
                launch(exactAlarmTarget)
            }
            if (exactResult) {
                return RoutineAlarmPermissionSettingsLaunchResult.ExactAlarmSettingsOpened
            }
        }

        val fallbackResult = runCatchingRecoverable {
            launch(appDetailsTarget)
        }
        return if (fallbackResult) {
            RoutineAlarmPermissionSettingsLaunchResult.AppDetailsFallbackOpened
        } else {
            RoutineAlarmPermissionSettingsLaunchResult.Unavailable
        }
    }

    private fun runCatchingRecoverable(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
