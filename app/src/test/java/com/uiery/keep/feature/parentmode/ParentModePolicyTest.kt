package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
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

    @Test
    fun expiredActiveSessionCannotBeEndedOrExtendedAsParentAction() {
        val expiredByClockSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )

        val extendDecision = ParentModePolicy.requestParentAction(
            session = expiredByClockSession,
            action = ParentModeParentAction.Extend(extensionMinutes = 10),
            pinState = ParentModePinState.Verified,
            nowMillis = 61_000L,
        )
        val endDecision = ParentModePolicy.requestParentAction(
            session = expiredByClockSession,
            action = ParentModeParentAction.EndNow,
            pinState = ParentModePinState.Verified,
            nowMillis = 61_000L,
        )

        assertEquals(ParentModeActionDecision.Expired, extendDecision)
        assertEquals(ParentModeActionDecision.Expired, endDecision)
    }

    @Test
    fun activeControlsRefreshDelayIsBoundedToExpiryTime() {
        val activeSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )
        val expiredSession = activeSession.copy(state = ParentModeSessionState.Expired)

        assertEquals(59_000L, activeSessionRefreshDelayMillis(activeSession, nowMillis = 2_000L))
        assertEquals(0L, activeSessionRefreshDelayMillis(activeSession, nowMillis = 61_000L))
        assertEquals(null, activeSessionRefreshDelayMillis(expiredSession, nowMillis = 2_000L))
    }

    /**
     * An expired session locks every app, and this button is the only thing that lifts it. Labelling
     * it "start parent mode again" hides the exit behind a name for starting another lock, which is
     * the last thing someone staring at a locked device goes looking for. A session already ended by
     * PIN locks nothing, so there "start another" is the honest label.
     */
    @Test
    fun finishedSessionActionSaysUnlockOnlyWhileTheDeviceIsStillLocked() {
        assertEquals(
            ParentModeFinishedAction.EndAndUnlock,
            ParentModePolicy.finishedSessionAction(ParentModeSessionState.Expired),
        )
        assertEquals(
            ParentModeFinishedAction.StartAnother,
            ParentModePolicy.finishedSessionAction(ParentModeSessionState.UnlockedByPin),
        )
        assertEquals(
            ParentModeFinishedAction.StartAnother,
            ParentModePolicy.finishedSessionAction(ParentModeSessionState.Cancelled),
        )
    }

    /**
     * 허용 앱 목록은 상한이 없어서 앱을 많이 고르면 아래에 있는 허용 시간 카드를 접히는 선 밖으로
     * 밀어냈다. 목록은 이미 고른 것을 확인시켜 주는 자리이고 편집은 `앱 선택 화면에서 조정`이
     * 맡으므로, 앞의 몇 개만 보여주고 나머지는 개수로 접는다.
     */
    @Test
    fun allowedAppsPreviewCapsTheListAndReportsWhatItFolded() {
        val preview = allowedAppsPreview(
            setOf("com.e.app", "com.a.app", "com.d.app", "com.b.app", "com.c.app"),
        )

        assertEquals(listOf("com.a.app", "com.b.app", "com.c.app"), preview.visible)
        assertEquals(2, preview.hiddenCount)
    }

    @Test
    fun allowedAppsPreviewFoldsNothingWhenTheListFits() {
        val preview = allowedAppsPreview(setOf("com.b.app", "com.a.app"))

        assertEquals(listOf("com.a.app", "com.b.app"), preview.visible)
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun allowedAppsPreviewOnTheCapBoundaryStillFoldsNothing() {
        val preview = allowedAppsPreview(setOf("com.c.app", "com.a.app", "com.b.app"))

        assertEquals(3, preview.visible.size)
        assertEquals(0, preview.hiddenCount)
    }

    /**
     * 접기 표시도 목록의 한 줄을 쓴다. 한 개만 접히면 줄 수가 그대로라 아낀 것 없이 무엇이 들어
     * 있는지만 감추는 셈이라, 그때는 접지 않는다.
     */
    @Test
    fun allowedAppsPreviewDoesNotFoldASingleAppBecauseFoldingItSavesNoRow() {
        val preview = allowedAppsPreview(
            setOf("com.d.app", "com.a.app", "com.c.app", "com.b.app"),
        )

        assertEquals(
            listOf("com.a.app", "com.b.app", "com.c.app", "com.d.app"),
            preview.visible,
        )
        assertEquals(0, preview.hiddenCount)
    }

    @Test
    fun allowedAppsPreviewOfAnEmptySelectionIsEmpty() {
        val preview = allowedAppsPreview(emptySet())

        assertEquals(emptyList<String>(), preview.visible)
        assertEquals(0, preview.hiddenCount)
    }
}
