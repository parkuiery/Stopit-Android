package com.uiery.keep.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyUnlockExpiryIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        clearEmergencyUnlockState()
        cancelEmergencyUnlockNotification(context)
    }

    @After
    fun tearDown() = runBlocking {
        clearEmergencyUnlockState()
        cancelEmergencyUnlockNotification(context)
    }

    @Test
    fun emergencyUnlockNotificationHelperWithoutPostNotificationsPermissionReturnsPermissionDeniedAndDoesNotPostNotification() {
        assertTrue(
            "Disable POST_NOTIFICATION with host adb/appops before running this focused test",
            !NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        val helper = EmergencyUnlockNotificationHelper(context)

        assertEquals(
            EmergencyUnlockNotificationPostResult.PermissionDenied,
            helper.showCountdown(remainingSeconds = 60, totalSeconds = 60),
        )
        assertFalse(activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID))
    }

    @Test
    fun handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage() = runBlocking {
        grantPostNotificationsPermission()
        val blockedPackage = "com.example.blocked"
        val expireTimeMillis = 1_000L
        val helper = EmergencyUnlockNotificationHelper(context)
        assertEquals(
            EmergencyUnlockNotificationPostResult.Posted,
            helper.showCountdown(remainingSeconds = 60, totalSeconds = 60),
        )
        waitUntil("Emergency unlock notification should become active after showCountdown") {
            activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
        }

        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf(blockedPackage)
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTimeMillis
        }
        EmergencyUnlockState.current = EmergencyUnlockData(
            unlockedApps = setOf(blockedPackage),
            expireTimeMillis = expireTimeMillis,
        )

        val resolution = handleExpiredEmergencyUnlockForContext(
            context = context,
            expectedExpireTimeMillis = expireTimeMillis,
            currentExpireTimeMillis = expireTimeMillis,
            expiredUnlockedApps = setOf(blockedPackage),
            foregroundPackage = blockedPackage,
            applicationId = context.packageName,
            isForegroundStillEmergencyUnlocked = false,
            clearExpiredEmergencyUnlockState = BlockingStateStore(context.dataStore)::clearEmergencyUnlockRuntimeState,
            nowMillis = expireTimeMillis,
        )

        assertEquals(
            EmergencyUnlockExpiryResolution(
                shouldClearState = true,
                packageToReblock = blockedPackage,
            ),
            resolution,
        )
        assertEquals(emptySet<String>(), storedUnlockedApps())
        assertNull(storedExpireTimeMillis())
        assertEquals(EmergencyUnlockData.EMPTY, EmergencyUnlockState.current)
        waitUntil("Emergency unlock notification should be cancelled after expiry handling") {
            !activeNotificationIds().contains(EmergencyUnlockNotificationHelper.NOTIFICATION_ID)
        }
    }

    private fun grantPostNotificationsPermission() {
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} POST_NOTIFICATION allow",
        ).close()
        waitUntil("POST_NOTIFICATIONS should be enabled for test setup") {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private suspend fun clearEmergencyUnlockState() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
        EmergencyUnlockState.current = EmergencyUnlockData.EMPTY
    }

    private suspend fun storedUnlockedApps(): Set<String> =
        context.dataStore.data.first()[PreferencesKey.EMERGENCY_UNLOCK_APPS] ?: emptySet()

    private suspend fun storedExpireTimeMillis(): Long? =
        context.dataStore.data.first()[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME]

    private fun activeNotificationIds(): Set<Int> {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.map { it.id }.toSet()
    }

    private fun waitUntil(message: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(50)
        }
        throw AssertionError(message)
    }
}
