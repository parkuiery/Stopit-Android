package com.uiery.keep.feature.parentmode

import com.uiery.keep.analytics.AnalyticsParentModeAllowedAppCountBucket
import com.uiery.keep.analytics.AnalyticsParentModeDurationBucket
import com.uiery.keep.analytics.AnalyticsParentModeEndReason
import com.uiery.keep.data.parentmode.ParentModeSessionStore
import com.uiery.keep.analytics.AnalyticsParentModeExtensionMinutesBucket
import com.uiery.keep.analytics.AnalyticsParentModePinResult
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            guardianPin = "",
            guardianPinConfirmation = "",
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
            guardianPin = "1234",
            guardianPinConfirmation = "1234",
            nowMillis = 1_000L,
        )

        val expectedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app", "com.learning.app"),
            state = ParentModeSessionState.Active,
        )
        val storedSession = store.read()
        assertEquals(
            ParentModeSessionControllerResult.Started(expectedSession),
            (result as ParentModeSessionControllerResult.Started).let {
                ParentModeSessionControllerResult.Started(it.session.copy(guardianPin = null))
            },
        )
        assertEquals(expectedSession, storedSession?.copy(guardianPin = null))
        // The PIN survives the restart the parent will need it across, but only as a digest.
        assertNotNull(storedSession?.guardianPin)
        assertNotEquals("1234", storedSession?.guardianPin?.hash)
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
            guardianPin = GUARDIAN_PIN,
        )
        store.save(active)
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.extend(
            extensionMinutes = 10,
            pinAttempt = "9999",
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
                guardianPin = GUARDIAN_PIN,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.extend(
            extensionMinutes = 10,
            pinAttempt = "1234",
            nowMillis = 10_000L,
        )

        val updatedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
            guardianPin = GUARDIAN_PIN,
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
                guardianPin = GUARDIAN_PIN,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.endNow(
            pinAttempt = "1234",
            nowMillis = 60_000L,
        )

        val endedSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 60_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.UnlockedByPin,
            guardianPin = GUARDIAN_PIN,
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
    fun extendFinishedSessionDoesNotReactivateOrTrackAnalytics() = runBlocking {
        listOf(
            ParentModeSessionState.Expired,
            ParentModeSessionState.UnlockedByPin,
            ParentModeSessionState.Cancelled,
        ).forEach { finishedState ->
            val store = ParentModeSessionStore(FakeDataStore())
            val finishedSession = ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 61_000L,
                durationMinutes = 1,
                allowedApps = setOf("com.video.app"),
                state = finishedState,
                guardianPin = GUARDIAN_PIN,
            )
            store.save(finishedSession)
            val analytics = RecordingParentModeAnalytics()
            val controller = ParentModeSessionController(store, analytics)

            val result = controller.extend(
                extensionMinutes = 10,
                pinAttempt = "1234",
                nowMillis = 62_000L,
            )

            assertEquals(ParentModeSessionControllerResult.NoStateChange(finishedSession), result)
            assertEquals(finishedSession, store.read())
            assertEquals(emptyList<ParentModeAnalyticsRecord>(), analytics.records)
        }
    }

    @Test
    fun endFinishedSessionDoesNotRetrackCompletionAnalytics() = runBlocking {
        listOf(
            ParentModeSessionState.Expired,
            ParentModeSessionState.UnlockedByPin,
            ParentModeSessionState.Cancelled,
        ).forEach { finishedState ->
            val store = ParentModeSessionStore(FakeDataStore())
            val finishedSession = ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 61_000L,
                durationMinutes = 1,
                allowedApps = setOf("com.video.app"),
                state = finishedState,
                guardianPin = GUARDIAN_PIN,
            )
            store.save(finishedSession)
            val analytics = RecordingParentModeAnalytics()
            val controller = ParentModeSessionController(store, analytics)

            val result = controller.endNow(
                pinAttempt = "1234",
                nowMillis = 62_000L,
            )

            assertEquals(ParentModeSessionControllerResult.NoStateChange(finishedSession), result)
            assertEquals(finishedSession, store.read())
            assertEquals(emptyList<ParentModeAnalyticsRecord>(), analytics.records)
        }
    }

    @Test
    fun extendExpiredActiveSessionPersistsTimeExpiredInsteadOfExtendingFromStaleExpiry() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 61_000L,
                durationMinutes = 1,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
                guardianPin = GUARDIAN_PIN,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.extend(
            extensionMinutes = 10,
            pinAttempt = "1234",
            nowMillis = 61_000L,
        )

        val expiredSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
            guardianPin = GUARDIAN_PIN,
        )
        assertEquals(ParentModeSessionControllerResult.Expired(expiredSession), result)
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

    @Test
    fun endExpiredActiveSessionPersistsTimeExpiredInsteadOfPinUnlocked() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 61_000L,
                durationMinutes = 1,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
                guardianPin = GUARDIAN_PIN,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        val result = controller.endNow(
            pinAttempt = "1234",
            nowMillis = 61_000L,
        )

        val expiredSession = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 61_000L,
            durationMinutes = 1,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Expired,
            guardianPin = GUARDIAN_PIN,
        )
        assertEquals(ParentModeSessionControllerResult.Expired(expiredSession), result)
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
                guardianPin = GUARDIAN_PIN,
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
            guardianPin = GUARDIAN_PIN,
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

    @Test
    fun endNowRejectsAPinThatIsNotTheOneTheSessionStarted() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        val active = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 1_201_000L,
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            state = ParentModeSessionState.Active,
            guardianPin = GUARDIAN_PIN,
        )
        store.save(active)
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        // The digits agree with themselves, which used to be the whole check.
        val rejected = controller.endNow(pinAttempt = "9999", nowMillis = 60_000L)

        assertEquals(ParentModeSessionControllerResult.PinRequired, rejected)
        assertEquals(active, store.read())
        assertEquals(emptyList<ParentModeAnalyticsRecord>(), analytics.records)

        val accepted = controller.endNow(pinAttempt = "1234", nowMillis = 60_000L)

        assertEquals(
            ParentModeSessionState.UnlockedByPin,
            (accepted as ParentModeSessionControllerResult.Ended).session.state,
        )
    }

    @Test
    fun theTypedPinNeverReachesTheStoredPreferences() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ParentModeSessionStore(dataStore)
        val controller = ParentModeSessionController(store, RecordingParentModeAnalytics())

        controller.start(
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            guardianPin = "482913",
            guardianPinConfirmation = "482913",
            nowMillis = 1_000L,
        )

        val storedValues = dataStore.snapshot().asMap().values.map(Any::toString)
        assertTrue(storedValues.none { it.contains("482913") })
        assertNotNull(store.read()?.guardianPin)
    }

    @Test
    fun clearingASessionAlsoClearsTheStoredPinDigest() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ParentModeSessionStore(dataStore)
        val controller = ParentModeSessionController(store, RecordingParentModeAnalytics())
        controller.start(
            durationMinutes = 20,
            allowedApps = setOf("com.video.app"),
            guardianPin = "1234",
            guardianPinConfirmation = "1234",
            nowMillis = 1_000L,
        )
        controller.endNow(pinAttempt = "1234", nowMillis = 60_000L)

        controller.clearFinishedSession()

        assertNull(store.read())
        assertTrue(dataStore.snapshot().asMap().keys.none { it.name.startsWith("parent_mode_pin") })
    }

    @Test
    fun aSessionStoredBeforePinsWereSavedCanStillBeEndedByTheParent() = runBlocking {
        val store = ParentModeSessionStore(FakeDataStore())
        // Exactly what an upgrade finds on disk: a running session with no digest beside it.
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 1_201_000L,
                durationMinutes = 20,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
                guardianPin = null,
            ),
        )
        val analytics = RecordingParentModeAnalytics()
        val controller = ParentModeSessionController(store, analytics)

        assertNull(store.read()?.guardianPin)

        val result = controller.endNow(pinAttempt = "", nowMillis = 60_000L)

        assertEquals(
            ParentModeSessionState.UnlockedByPin,
            (result as ParentModeSessionControllerResult.Ended).session.state,
        )
        assertEquals(
            ParentModeAnalyticsRecord.UnlockedByPin(
                pinResult = AnalyticsParentModePinResult.NOT_CONFIGURED,
                endReason = AnalyticsParentModeEndReason.PIN_UNLOCKED,
            ),
            analytics.records.first(),
        )
    }
}


/** One digest for the whole file: the salt is random per call, so a shared value keeps the
 *  seeded sessions comparable with what the store reads back. */
private val GUARDIAN_PIN = ParentModePolicy.digestGuardianPin("1234")

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