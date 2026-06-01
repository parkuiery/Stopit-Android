package com.uiery.keep.feature.onboarding.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyNotificationPermissionActionTest {

    @Test
    fun firstLegacyTapOpensSettingsAndTracksSettingsOpened() {
        assertEquals(
            LegacyNotificationPermissionAction.OpenSettingsFirstTime,
            resolveLegacyNotificationPermissionAction(
                hasVisitedSettings = false,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun returningWithoutEnablingNotificationsReopensSettingsAsDeniedRetry() {
        assertEquals(
            LegacyNotificationPermissionAction.ReopenSettingsAfterDenied,
            resolveLegacyNotificationPermissionAction(
                hasVisitedSettings = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun returningWithNotificationsEnabledAdvancesOnboarding() {
        assertEquals(
            LegacyNotificationPermissionAction.GrantAndContinue,
            resolveLegacyNotificationPermissionAction(
                hasVisitedSettings = true,
                notificationsEnabled = true,
            ),
        )
    }
}
