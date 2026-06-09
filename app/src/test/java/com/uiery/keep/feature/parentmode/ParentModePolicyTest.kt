package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentModePolicyTest {
    @Test
    fun setupRequiresPositiveDurationAndAtLeastOneAllowedApp() {
        val valid = ParentModePolicy.validateSetup(
            durationMinutes = 20,
            allowedAppCount = 2,
            pinState = ParentModePinState.Verified,
        )

        assertTrue(valid.canStart)
        assertEquals(emptySet<ParentModeSetupIssue>(), valid.issues)

        val invalid = ParentModePolicy.validateSetup(
            durationMinutes = 0,
            allowedAppCount = 0,
            pinState = ParentModePinState.NotConfigured,
        )

        assertFalse(invalid.canStart)
        assertEquals(
            setOf(
                ParentModeSetupIssue.InvalidDuration,
                ParentModeSetupIssue.NoAllowedApps,
                ParentModeSetupIssue.PinNotVerified,
            ),
            invalid.issues,
        )
    }

    @Test
    fun durationMinutesAreBucketedWithoutRawTimestamps() {
        assertEquals(AnalyticsParentModeDurationBucket.ONE_TO_NINE, ParentModePolicy.durationBucket(9))
        assertEquals(AnalyticsParentModeDurationBucket.TEN, ParentModePolicy.durationBucket(10))
        assertEquals(AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY, ParentModePolicy.durationBucket(20))
        assertEquals(AnalyticsParentModeDurationBucket.TWENTY_ONE_TO_THIRTY, ParentModePolicy.durationBucket(30))
        assertEquals(AnalyticsParentModeDurationBucket.THIRTY_ONE_TO_SIXTY, ParentModePolicy.durationBucket(60))
        assertEquals(AnalyticsParentModeDurationBucket.SIXTY_ONE_PLUS, ParentModePolicy.durationBucket(61))
    }

    @Test
    fun allowedAppCountIsBucketedWithoutPackageNames() {
        assertEquals(AnalyticsParentModeAllowedAppCountBucket.ONE, ParentModePolicy.allowedAppCountBucket(1))
        assertEquals(AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE, ParentModePolicy.allowedAppCountBucket(3))
        assertEquals(AnalyticsParentModeAllowedAppCountBucket.FOUR_TO_SIX, ParentModePolicy.allowedAppCountBucket(6))
        assertEquals(AnalyticsParentModeAllowedAppCountBucket.SEVEN_PLUS, ParentModePolicy.allowedAppCountBucket(7))
    }

    @Test
    fun startSessionCapturesExpiryAndAllowedAppsWithoutLeakingRawAnalytics() {
        val session = ParentModePolicy.startSession(
            startedAtMillis = 1_000L,
            durationMinutes = 10,
            allowedApps = setOf("com.video.app", "com.learning.app"),
        )

        assertEquals(1_000L, session.startedAtMillis)
        assertEquals(601_000L, session.expiresAtMillis)
        assertEquals(10, session.durationMinutes)
        assertEquals(setOf("com.video.app", "com.learning.app"), session.allowedApps)
        assertEquals(ParentModeSessionState.Active, session.state)
    }

    @Test
    fun activeSessionExpiresAtOrAfterExpiryTime() {
        val session = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )

        assertEquals(ParentModeSessionState.Active, ParentModePolicy.resolveState(session, nowMillis = 60_999L))
        assertEquals(ParentModeSessionState.Expired, ParentModePolicy.resolveState(session, nowMillis = 61_000L))
    }

    @Test
    fun activeSessionAllowsOnlyExplicitlyAllowedPackagesAndExpiryBlocksAllowedApps() {
        val session = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )

        assertFalse(ParentModePolicy.shouldBlockPackage(session, packageName = "com.video.app", nowMillis = 2_000L))
        assertTrue(ParentModePolicy.shouldBlockPackage(session, packageName = "com.game.app", nowMillis = 2_000L))
        assertTrue(ParentModePolicy.shouldBlockPackage(session, packageName = "com.video.app", nowMillis = 61_000L))
    }

    @Test
    fun parentPinIsRequiredToEndOrExtendActiveSession() {
        val activeSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )

        val denied = ParentModePolicy.requestParentAction(
            session = activeSession,
            action = ParentModeParentAction.EndNow,
            pinState = ParentModePinState.Failed,
            nowMillis = 2_000L,
        )
        assertEquals(ParentModeActionDecision.PinRequired, denied)

        val allowed = ParentModePolicy.requestParentAction(
            session = activeSession,
            action = ParentModeParentAction.Extend(extensionMinutes = 10),
            pinState = ParentModePinState.Verified,
            nowMillis = 2_000L,
        )
        assertEquals(
            ParentModeActionDecision.Extend(
                expiresAtMillis = 661_000L,
                extensionMinutesBucket = AnalyticsParentModeExtensionMinutesBucket.TEN,
            ),
            allowed,
        )
    }

    @Test
    fun parentModeExtensionRejectsNonPositiveDurationsEvenAfterPinVerification() {
        val activeSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )

        val zeroMinuteExtension = ParentModePolicy.requestParentAction(
            session = activeSession,
            action = ParentModeParentAction.Extend(extensionMinutes = 0),
            pinState = ParentModePinState.Verified,
            nowMillis = 2_000L,
        )
        val negativeMinuteExtension = ParentModePolicy.requestParentAction(
            session = activeSession,
            action = ParentModeParentAction.Extend(extensionMinutes = -5),
            pinState = ParentModePinState.Verified,
            nowMillis = 2_000L,
        )

        assertEquals(ParentModeActionDecision.InvalidExtension, zeroMinuteExtension)
        assertEquals(ParentModeActionDecision.InvalidExtension, negativeMinuteExtension)
    }
}
