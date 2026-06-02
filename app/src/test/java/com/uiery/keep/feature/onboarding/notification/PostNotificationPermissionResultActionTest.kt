package com.uiery.keep.feature.onboarding.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class PostNotificationPermissionResultActionTest {

    @Test
    fun grantedRuntimePermissionRecordsGrantAndContinuesToAppSelection() {
        assertEquals(
            PostNotificationPermissionResultAction.RecordGrantAndContinue,
            resolvePostNotificationPermissionResultAction(isGranted = true),
        )
    }

    @Test
    fun deniedRuntimePermissionRecordsDenialAndStillContinuesToAppSelection() {
        assertEquals(
            PostNotificationPermissionResultAction.RecordDenialAndContinue,
            resolvePostNotificationPermissionResultAction(isGranted = false),
        )
    }
}
