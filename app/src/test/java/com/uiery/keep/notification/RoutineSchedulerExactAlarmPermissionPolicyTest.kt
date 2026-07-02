package com.uiery.keep.notification

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSchedulerExactAlarmPermissionPolicyTest {

    @Test
    fun modeDefaultUsesAlarmManagerAvailabilityAsAuthoritativeDefaultState() {
        assertTrue(
            resolveExactAlarmAvailability(
                appOpsMode = AppOpsManager.MODE_DEFAULT,
                alarmManagerAllowed = true,
            ),
        )

        assertFalse(
            resolveExactAlarmAvailability(
                appOpsMode = AppOpsManager.MODE_DEFAULT,
                alarmManagerAllowed = false,
            ),
        )
    }

    @Test
    fun explicitDeniedAppOpsAlwaysBlocksEvenWhenAlarmManagerStillReportsAllowed() {
        assertFalse(
            resolveExactAlarmAvailability(
                appOpsMode = AppOpsManager.MODE_IGNORED,
                alarmManagerAllowed = true,
            ),
        )
    }

    @Test
    fun explicitAllowedAppOpsStillRequiresAlarmManagerAvailability() {
        assertTrue(
            resolveExactAlarmAvailability(
                appOpsMode = AppOpsManager.MODE_ALLOWED,
                alarmManagerAllowed = true,
            ),
        )
        assertFalse(
            resolveExactAlarmAvailability(
                appOpsMode = AppOpsManager.MODE_ALLOWED,
                alarmManagerAllowed = false,
            ),
        )
    }
}
