package com.uiery.keep.feature.parentmode

import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParentModeSessionStoreTest {
    @Test
    fun saveAndReadRoundTripsActiveSessionWithoutRawAnalyticsPayload() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ParentModeSessionStore(dataStore)
        val session = ParentModeSession(
            startedAtMillis = 1_000L,
            expiresAtMillis = 601_000L,
            durationMinutes = 10,
            allowedApps = setOf("com.video.app", "com.learning.app"),
            state = ParentModeSessionState.Active,
        )

        store.save(session)

        val restored = store.read()
        assertEquals(session, restored)
        val snapshot = dataStore.snapshot()
        assertEquals(1_000L, snapshot[PreferencesKey.PARENT_MODE_STARTED_AT])
        assertEquals(601_000L, snapshot[PreferencesKey.PARENT_MODE_EXPIRES_AT])
        assertEquals(10, snapshot[PreferencesKey.PARENT_MODE_DURATION_MINUTES])
        assertEquals(setOf("com.video.app", "com.learning.app"), snapshot[PreferencesKey.PARENT_MODE_ALLOWED_APPS])
        assertEquals("active", snapshot[PreferencesKey.PARENT_MODE_STATE])
    }

    @Test
    fun readReturnsNullWhenRequiredSessionFieldsAreMissingOrInvalid() = runBlocking {
        val missingAllowedAppsStore = ParentModeSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.PARENT_MODE_STARTED_AT] = 1_000L
                this[PreferencesKey.PARENT_MODE_EXPIRES_AT] = 601_000L
                this[PreferencesKey.PARENT_MODE_DURATION_MINUTES] = 10
                this[PreferencesKey.PARENT_MODE_STATE] = "active"
            },
        )
        val invalidStateStore = ParentModeSessionStore(
            FakeDataStore.withPrefs {
                this[PreferencesKey.PARENT_MODE_STARTED_AT] = 1_000L
                this[PreferencesKey.PARENT_MODE_EXPIRES_AT] = 601_000L
                this[PreferencesKey.PARENT_MODE_DURATION_MINUTES] = 10
                this[PreferencesKey.PARENT_MODE_ALLOWED_APPS] = setOf("com.video.app")
                this[PreferencesKey.PARENT_MODE_STATE] = "unknown"
            },
        )

        assertNull(missingAllowedAppsStore.read())
        assertNull(invalidStateStore.read())
    }

    @Test
    fun clearRemovesEveryParentModeRuntimeKey() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ParentModeSessionStore(dataStore)
        store.save(
            ParentModeSession(
                startedAtMillis = 1_000L,
                expiresAtMillis = 601_000L,
                durationMinutes = 10,
                allowedApps = setOf("com.video.app"),
                state = ParentModeSessionState.Active,
            ),
        )

        store.clear()

        val snapshot = dataStore.snapshot()
        assertNull(store.read())
        assertNull(snapshot[PreferencesKey.PARENT_MODE_STARTED_AT])
        assertNull(snapshot[PreferencesKey.PARENT_MODE_EXPIRES_AT])
        assertNull(snapshot[PreferencesKey.PARENT_MODE_DURATION_MINUTES])
        assertNull(snapshot[PreferencesKey.PARENT_MODE_ALLOWED_APPS])
        assertNull(snapshot[PreferencesKey.PARENT_MODE_STATE])
    }
}
