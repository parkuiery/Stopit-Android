package com.uiery.keep.datastore

import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockSettingsStoreTest {
    @Test
    fun settingsSanitizesInvalidPersistedValuesThroughSingleTypedBoundary() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = false
                this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = 99
                this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("15", "3", "bad", "15")
                this[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = false
            }
        val store = EmergencyUnlockSettingsStore(dataStore)

        val settings = store.settings.first()

        assertFalse(settings.enabled)
        assertEquals(DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT, settings.dailyLimit)
        assertEquals(listOf(3, 15), settings.durationOptions)
        assertFalse(settings.reasonRequired)
        assertTrue(settings.autoResetEnabled)
        assertEquals(0L, settings.manualResetAtMillis)
    }

    @Test
    fun writeOperationsPersistSanitizedEmergencyUnlockSettingsKeys() = runBlocking {
        val dataStore = FakeDataStore()
        val store = EmergencyUnlockSettingsStore(dataStore)

        store.setEnabled(false)
        store.setDailyLimit(99)
        store.toggleDuration(15)
        store.toggleDuration(3)
        store.toggleDuration(3)
        store.toggleDuration(99)
        store.setReasonRequired(false)
        store.setAutoResetEnabled(false)
        store.markManualReset(nowMillis = 123_456L)

        val snapshot = dataStore.snapshot()
        assertEquals(false, snapshot[PreferencesKey.EMERGENCY_UNLOCK_ENABLED])
        assertEquals(DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT, snapshot[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT])
        assertEquals(setOf("3", "5", "10", "15"), snapshot[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS])
        assertEquals(false, snapshot[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED])
        assertEquals(false, snapshot[PreferencesKey.EMERGENCY_UNLOCK_AUTO_RESET_ENABLED])
        assertEquals(123_456L, snapshot[PreferencesKey.EMERGENCY_UNLOCK_MANUAL_RESET_AT])
    }

    @Test
    fun toggleDurationNeverRemovesTheLastAllowedOption() = runBlocking {
        val dataStore =
            FakeDataStore.withPrefs {
                this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = setOf("5")
            }
        val store = EmergencyUnlockSettingsStore(dataStore)

        store.toggleDuration(5)

        assertEquals(setOf("5"), dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS])
        assertEquals(DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS, EmergencyUnlockSettingsStore(FakeDataStore()).settings.first().durationOptions)
        assertTrue(5 in store.settings.first().durationOptions)
    }
}
