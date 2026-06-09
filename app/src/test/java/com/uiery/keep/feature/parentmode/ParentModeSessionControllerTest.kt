package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeEndReason
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import com.uiery.keep.analytics.AnalyticsParentModePinResult
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParentModeSessionControllerTest {
    @Test
    fun startRequiresValidDurationAllowedAppsAndVerifiedPinBeforePersisting() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.start(
            durationMinutes = 0,
            allowedApps = emptySet(),
            pinState = ParentModePinState.NotConfigured,
            nowMillis = 1_000L,
        )

        assertEquals(
            ParentModeSessionControllerResult.SetupBlocked(
                issues = setOf(
                    ParentModeSetupIssue.InvalidDuration,
                    ParentModeSetupIssue.NoAllowedApps,
                    ParentModeSetupIssue.PinNotVerified,
                ),
            ),
            result,
        )
        assertNull(store.read())
        assertEquals(emptyList<ParentModeAnalyticsRecord>(), analytics.records)
    }

    @Test
    fun startPersistsActiveSessionAndTracksOnlyBucketedSetupAndStartedEvents() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.start(
            durationMinutes = 20,
            allowedApps = setOf("com.video.app", "com.learning.app"),
            pinState = ParentModePinState.Verified,
            nowMillis = 1_000L,
        )

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app", "com.learning.app"),
            state = ParentModeSessionState.Active,
        )
        assertEquals(ParentModeSessionControllerResult.Started(expectedSession), result)
        assertEquals(expectedSession, store.read())
        assertEquals(
            listOf(
                ParentModeAnalyticsRecord.DurationSelected(AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY),
                ParentModeAnalyticsRecord.AllowedAppsSelected(AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE),
                ParentModeAnalyticsRecord.Started(
                    durationMinutesBucket = AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY,
                    allowedAppCountBucket = AnalyticsParentModeAllowedAppCountBucket.TWO_TO_THREE,
                ),
            ),
            analytics.records,
        )
    }

    @Test
    fun extendRequiresVerifiedPinBeforeChangingSessionOrTrackingAnalytics() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val active = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 601_000L,
            durationMinutes = 10,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )
        store.save(active)
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.extend(
            extensionMinutes = 10,
            pinState = ParentModePinState.Failed,
            nowMillis = 10_000L,
        )

        assertEquals(ParentModeSessionControllerResult.PinRequired, result)
        assertEquals(active, store.read())
        assertEquals(emptyList<ParentModeAnalyticsRecord>(), analytics.records)
    }

    @Test
    fun extendPersistsUpdatedExpiryAndDurationAfterVerifiedPin() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 601_000L,
                durationMinutes = 10,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.extend(
            extensionMinutes = 10,
            pinState = ParentModePinState.Verified,
            nowMillis = 10_000L,
        )

        val updatedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
        )
        assertEquals(ParentModeSessionControllerResult.Extended(updatedSession), result)
        assertEquals(updatedSession, store.read())
        assertEquals(
            listOf(ParentModeAnalyticsRecord.Extended(AnalyticsParentModeExtensionMinutesBucket.TEN)),
            analytics.records,
        )
    }

    @Test
    fun endNowPersistsUnlockedStateAndTracksPinUnlockPlusCompletedEvents() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 1_201_000L,
                durationMinutes = 20,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.endNow(
            pinState = ParentModePinState.Verified,
            nowMillis = 60_000L,
        )

        val endedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 60_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.UnlockedByPin,
        )
        assertEquals(ParentModeSessionControllerResult.Ended(endedSession), result)
        assertEquals(endedSession, store.read())
        assertEquals(
            listOf(
                ParentModeAnalyticsRecord.UnlockedByPin(
                    pinResult = AnalyticsParentModePinResult.SUCCESS,
                    endReason = AnalyticsParentModeEndReason.PIN_UNLOCKED,
                ),
                ParentModeAnalyticsRecord.Completed(
                    durationMinutesBucket = AnalyticsParentModeDurationBucket.ELEVEN_TO_TWENTY,
                    endReason = AnalyticsParentModeEndReason.PIN_UNLOCKED,
                ),
            ),
            analytics.records,
        )
    }

    @Test
    fun markExpiredIfNeededPersistsExpiredSessionAndTracksCompletionOnce() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 61_000L,
                durationMinutes = 1,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val firstResult = controller.markExpiredIfNeeded(nowMillis = 61_000L)
        val secondResult = controller.markExpiredIfNeeded(nowMillis = 62_000L)

        val expiredSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
        )
        assertEquals(ParentModeSessionControllerResult.Expired(expiredSession), firstResult)
        assertEquals(ParentModeSessionControllerResult.NoStateChange(expiredSession), secondResult)
        assertEquals(expiredSession, store.read())
        assertEquals(
            listOf(
                ParentModeAnalyticsRecord.Completed(
                    durationMinutesBucket = AnalyticsParentModeDurationBucket.ONE_TO_NINE,
                    endReason = AnalyticsParentModeEndReason.TIME_EXPIRED,
                ),
            ),
            analytics.records,
        )
    }
}

private class RecordingParentModeAnalytics : KeepAnalytics {
    val records = mutableListOf<ParentModeAnalyticsRecord>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(name: String, value: String) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit

    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit

    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit

    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit

    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackParentModeDurationSelected(durationMinutesBucket: String) {
        records += ParentModeAnalyticsRecord.DurationSelected(durationMinutesBucket)
    }

    override fun trackParentModeAllowedAppsSelected(allowedAppCountBucket: String) {
        records += ParentModeAnalyticsRecord.AllowedAppsSelected(allowedAppCountBucket)
    }

    override fun trackParentModeStarted(durationMinutesBucket: String, allowedAppCountBucket: String) {
        records += ParentModeAnalyticsRecord.Started(durationMinutesBucket, allowedAppCountBucket)
    }

    override fun trackParentModeCompleted(durationMinutesBucket: String, endReason: String) {
        records += ParentModeAnalyticsRecord.Completed(durationMinutesBucket, endReason)
    }

    override fun trackParentModeUnlockedByPin(pinResult: String, endReason: String) {
        records += ParentModeAnalyticsRecord.UnlockedByPin(pinResult, endReason)
    }

    override fun trackParentModeExtended(extensionMinutesBucket: String) {
        records += ParentModeAnalyticsRecord.Extended(extensionMinutesBucket)
    }
}

private sealed interface ParentModeAnalyticsRecord {
    data class DurationSelected(val durationMinutesBucket: String) : ParentModeAnalyticsRecord

    data class AllowedAppsSelected(val allowedAppCountBucket: String) : ParentModeAnalyticsRecord

    data class Started(
        val durationMinutesBucket: String,
        val allowedAppCountBucket: String,
    ) : ParentModeAnalyticsRecord

    data class Completed(
        val durationMinutesBucket: String,
        val endReason: String,
    ) : ParentModeAnalyticsRecord

    data class UnlockedByPin(
        val pinResult: String,
        val endReason: String,
    ) : ParentModeAnalyticsRecord

    data class Extended(val extensionMinutesBucket: String) : ParentModeAnalyticsRecord
}
