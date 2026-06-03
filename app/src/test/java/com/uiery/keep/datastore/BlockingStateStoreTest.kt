package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingStateStoreTest {

    @Test
    fun selectedAppPackagesDefaultEmptyAndRoundTripsThroughLegacyKey() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)
        val selectedPackages = setOf("com.example.alpha", "com.example.beta")

        assertEquals(emptySet<String>(), store.readSelectedAppPackages())
        assertEquals(BlockingSelectionState(), store.readSelectionState())

        store.saveSelectedAppPackages(selectedPackages)

        assertEquals(selectedPackages, store.readSelectedAppPackages())
        assertEquals(
            BlockingSelectionState(selectedAppPackages = selectedPackages),
            store.readSelectionState(),
        )
        assertEquals(selectedPackages, dataStore.snapshot()[PreferencesKey.SELECTED_APP_PACKAGES])
    }

    @Test
    fun markFirstLockConfiguredIfNeededOnlyTransitionsOnce() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        assertTrue(store.markFirstLockConfiguredIfNeeded())
        assertFalse(store.markFirstLockConfiguredIfNeeded())
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED])
    }

    @Test
    fun firstOpenTrackingPersistsTimestampOnlyOnce() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        assertTrue(store.markFirstOpenTrackedIfNeeded(timestampMillis = 1111L))
        assertFalse(store.markFirstOpenTrackedIfNeeded(timestampMillis = 2222L))

        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_OPEN])
        assertEquals(1111L, dataStore.snapshot()[PreferencesKey.FIRST_OPEN_TIMESTAMP])
    }

    @Test
    fun firstCoreActionStatePersistsFallbackTimestampWhenMissingAndTracksOnce() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        assertEquals(
            FirstCoreActionState(firstOpenTimestampMillis = 1234L, hasTrackedFirstCoreAction = false),
            store.readFirstCoreActionState(fallbackFirstOpenTimestampMillis = 1234L),
        )

        store.markFirstCoreActionTracked(firstOpenTimestampMillis = 1234L)

        assertEquals(
            FirstCoreActionState(firstOpenTimestampMillis = 1234L, hasTrackedFirstCoreAction = true),
            store.readFirstCoreActionState(fallbackFirstOpenTimestampMillis = 9999L),
        )
        assertEquals(1234L, dataStore.snapshot()[PreferencesKey.FIRST_OPEN_TIMESTAMP])
        assertEquals(true, dataStore.snapshot()[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION])
    }

    @Test
    fun successfulSessionCountIncrementsThroughBlockingBoundary() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        assertEquals(0, store.readSuccessfulSessionCount())
        assertEquals(1, store.incrementSuccessfulSessionCount())
        assertEquals(2, store.incrementSuccessfulSessionCount())
        assertEquals(2, store.readSuccessfulSessionCount())
    }

    @Test
    fun accessibilitySnapshotUsesSafeDefaultsAndLegacyKeys() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        dataStore.edit { prefs ->
            prefs[PreferencesKey.IS_KEEP] = true
            prefs[PreferencesKey.LOCK_TIME] = "2030-01-01T00:00:00"
            prefs[PreferencesKey.SELECTED_APP_PACKAGES] = setOf("com.example.blocked")
            prefs[PreferencesKey.PREVENT_UNINSTALL] = false
            prefs[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf("com.example.temp")
            prefs[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = 4567L
        }
        val store = BlockingStateStore(dataStore)

        assertEquals(
            AccessibilityBlockingSnapshot(
                isKeep = true,
                lockTime = "2030-01-01T00:00:00",
                selectedAppPackages = setOf("com.example.blocked"),
                preventUninstall = false,
                emergencyUnlockApps = setOf("com.example.temp"),
                emergencyUnlockExpireTimeMillis = 4567L,
            ),
            store.accessibilitySnapshot.first(),
        )
    }

    @Test
    fun clearEmergencyUnlockRuntimeStateRemovesRuntimeKeys() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        dataStore.edit { prefs ->
            prefs[PreferencesKey.EMERGENCY_UNLOCK_APPS] = setOf("com.example.temp")
            prefs[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = 4567L
        }
        val store = BlockingStateStore(dataStore)

        store.saveEmergencyUnlockRuntimeState(apps = setOf("com.example.next"), expireTimeMillis = 9999L)
        assertEquals(setOf("com.example.next"), dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_APPS])
        assertEquals(9999L, dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME])

        store.clearEmergencyUnlockRuntimeState()

        assertEquals(null, dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_APPS])
        assertEquals(null, dataStore.snapshot()[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME])
        assertEquals(emptySet<String>(), store.accessibilitySnapshot.first().emergencyUnlockApps)
        assertEquals(0L, store.accessibilitySnapshot.first().emergencyUnlockExpireTimeMillis)
    }

    @Test
    fun setIsNewAndKeepSessionValuesWriteLegacyKeys() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        store.setIsNew(false)
        store.setIsKeep(true)
        store.saveStartTime(111L)
        store.saveLockTime("2031-02-03T04:05:06")

        assertEquals(false, dataStore.snapshot()[PreferencesKey.IS_NEW])
        assertFalse(store.readIsNew())
        assertEquals(true, store.readIsKeep())
        assertEquals(111L, store.readStartTime())
        assertEquals("2031-02-03T04:05:06", store.readLockTime())
    }

    @Test
    fun preventUninstallDefaultsTrueAndRoundTripsThroughLegacyKey() = runBlocking {
        val dataStore = BlockingFakePreferencesDataStore()
        val store = BlockingStateStore(dataStore)

        assertTrue(store.accessibilitySnapshot.first().preventUninstall)

        store.setPreventUninstall(false)

        assertEquals(false, dataStore.snapshot()[PreferencesKey.PREVENT_UNINSTALL])
        assertFalse(store.accessibilitySnapshot.first().preventUninstall)
    }
}

private class BlockingFakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }

    fun snapshot(): Preferences = state.value
}
