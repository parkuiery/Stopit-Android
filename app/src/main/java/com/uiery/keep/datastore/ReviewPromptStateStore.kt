package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Typed boundary for review-prompt lifecycle preferences.
 *
 * Keep the underlying keys stable for backwards compatibility, but route feature code through this
 * store so pending-drain, cooldown, and backgrounding contracts cannot drift across call sites.
 */
@Singleton
class ReviewPromptStateStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    suspend fun readState(): ReviewPromptState {
        val preferences = dataStore.data.first()
        return ReviewPromptState(
            isPending = preferences[PreferencesKey.REVIEW_PENDING] == true,
            lastPromptAtMs = preferences[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS],
            lastBackgroundedAtMs = preferences[PreferencesKey.LAST_BACKGROUNDED_AT_MS],
        )
    }

    suspend fun markPending() {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.REVIEW_PENDING] = true
        }
    }

    suspend fun clearPending() {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.REVIEW_PENDING] = false
        }
    }

    suspend fun recordPromptShown(atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS] = atMillis
        }
    }

    suspend fun recordBackgrounded(atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.LAST_BACKGROUNDED_AT_MS] = atMillis
        }
    }
}

data class ReviewPromptState(
    val isPending: Boolean = false,
    val lastPromptAtMs: Long? = null,
    val lastBackgroundedAtMs: Long? = null,
)
