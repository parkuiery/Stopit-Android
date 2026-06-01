package com.uiery.keep.feature.onboarding.notification

internal sealed interface LegacyNotificationPermissionAction {
    data object OpenSettingsFirstTime : LegacyNotificationPermissionAction
    data object ReopenSettingsAfterDenied : LegacyNotificationPermissionAction
    data object GrantAndContinue : LegacyNotificationPermissionAction
}

internal fun resolveLegacyNotificationPermissionAction(
    hasVisitedSettings: Boolean,
    notificationsEnabled: Boolean,
): LegacyNotificationPermissionAction = when {
    !hasVisitedSettings -> LegacyNotificationPermissionAction.OpenSettingsFirstTime
    notificationsEnabled -> LegacyNotificationPermissionAction.GrantAndContinue
    else -> LegacyNotificationPermissionAction.ReopenSettingsAfterDenied
}
