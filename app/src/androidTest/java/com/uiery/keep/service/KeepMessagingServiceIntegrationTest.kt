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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepMessagingServiceIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        clearStoredToken()
    }

    @After
    fun tearDown() = runBlocking {
        clearStoredToken()
    }

    @Test
    fun persistNewTokenForContext_overwritesExistingStoredTokenViaEntryPoint() = runBlocking {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKey.FCM_TOKEN] = "stale-token"
        }

        KeepMessagingService.persistNewTokenForContext(context, "fresh-token")

        assertEquals("fresh-token", storedToken())
    }

    private suspend fun clearStoredToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.FCM_TOKEN)
        }
    }

    private suspend fun storedToken(): String? =
        context.dataStore.data.first()[PreferencesKey.FCM_TOKEN]
}
