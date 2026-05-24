package com.uiery.keep.service

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.datastore.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyUnlockExpiryIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        clearEmergencyUnlockState()
    }

    @After
    fun tearDown() = runBlocking {
        clearEmergencyUnlockState()
    }

    @Test
    fun handleExpiredEmergencyUnlockForContext_clearsStoredStateAndReturnsReblockPackage() = runBlocking {
        val blockedPackage = "com.example.blocked"
        val expireTimeMillis = 1_000L
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
}
