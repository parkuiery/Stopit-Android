package com.uiery.keep.feature.parentmode

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.data.parentmode.ParentModeSessionStore
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The guardian PIN against the DataStore file that actually ships, on a real device.
 *
 * The JVM tests prove the policy; this proves the storage. A salted hash is only worth having if the
 * bytes on disk really are the hash — a fake DataStore cannot show that, and the whole point of the
 * PIN is that someone holding the phone cannot read it back and retype it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ParentModeGuardianPinDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = ParentModeSessionStore(context.dataStore)
    private val controller = ParentModeSessionController(store, NoopParentModePinAnalytics)

    /** The real file the app writes, so what is asserted below is what an `adb run-as` would find. */
    private val preferencesFile: File
        get() = File(context.filesDir, "datastore/keep-datastore.preferences_pb")

    @Before
    fun clearAnyLeftoverSession() = runBlocking {
        controller.clearFinishedSession()
        store.clear()
    }



    @Test
    fun aDifferentPinCannotEndTheSessionAndTheRightOneCan() = runBlocking {
        val started = controller.start(
            durationMinutes = 20,
            allowedApps = setOf("com.android.chrome"),
            guardianPin = "1234",
            guardianPinConfirmation = "1234",
            nowMillis = System.currentTimeMillis(),
        )
        assertTrue(started is ParentModeSessionControllerResult.Started)

        // 아무 4자리나 두 번 치면 통과했던 그 경로.
        val wrong = controller.endNow(pinAttempt = "9999", nowMillis = System.currentTimeMillis())
        assertEquals(ParentModeSessionControllerResult.PinRequired, wrong)
        assertNotNull(store.read())
        assertEquals(
            com.uiery.keep.domain.parentmode.ParentModeSessionState.Active,
            store.read()?.state,
        )

        val right = controller.endNow(pinAttempt = "1234", nowMillis = System.currentTimeMillis())
        assertTrue(right is ParentModeSessionControllerResult.Ended)
        assertEquals(
            com.uiery.keep.domain.parentmode.ParentModeSessionState.UnlockedByPin,
            store.read()?.state,
        )

        controller.clearFinishedSession()
        assertNull(store.read())
    }

    @Test
    fun theTypedPinIsNotInTheFileOnDisk() = runBlocking {
        val pin = "482913"
        controller.start(
            durationMinutes = 20,
            allowedApps = setOf("com.android.chrome"),
            guardianPin = pin,
            guardianPinConfirmation = pin,
            nowMillis = System.currentTimeMillis(),
        )

        val digest = assertNotNull(store.read()?.guardianPin).let { store.read()!!.guardianPin!! }
        assertTrue(preferencesFile.exists())
        val bytes = preferencesFile.readBytes()

        // 원문은 파일 어디에도 없다. 해시와 salt 는 있다.
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains(pin))
        val asLatin = String(bytes, Charsets.ISO_8859_1)
        assertTrue(asLatin.contains(digest.hash))
        assertTrue(asLatin.contains(digest.salt))

        controller.endNow(pinAttempt = pin, nowMillis = System.currentTimeMillis())
        controller.clearFinishedSession()

        // clear 뒤에는 해시도 남지 않는다. 남기면 다음 세션이 이전 PIN을 물려받는다.
        val afterClear = String(preferencesFile.readBytes(), Charsets.ISO_8859_1)
        assertFalse(afterClear.contains(digest.hash))
        assertFalse(afterClear.contains(digest.salt))
    }
}

private object NoopParentModePinAnalytics : KeepAnalytics {
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
}
