package com.uiery.keep.data.parentmode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class ParentModeSessionStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    suspend fun save(session: ParentModeSession) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.PARENT_MODE_STARTED_AT] = session.startedAtMillis
            preferences[PreferencesKey.PARENT_MODE_EXPIRES_AT] = session.expiresAtMillis
            preferences[PreferencesKey.PARENT_MODE_DURATION_MINUTES] = session.durationMinutes
            preferences[PreferencesKey.PARENT_MODE_ALLOWED_APPS] = session.allowedApps
            preferences[PreferencesKey.PARENT_MODE_STATE] = session.state.toStoredValue()
        }
    }

    suspend fun read(): ParentModeSession? = dataStore.data.first().toParentModeSession()

    fun observe(): Flow<ParentModeSession?> = dataStore.data.map { preferences ->
        preferences.toParentModeSession()
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.PARENT_MODE_STARTED_AT)
            preferences.remove(PreferencesKey.PARENT_MODE_EXPIRES_AT)
            preferences.remove(PreferencesKey.PARENT_MODE_DURATION_MINUTES)
            preferences.remove(PreferencesKey.PARENT_MODE_ALLOWED_APPS)
            preferences.remove(PreferencesKey.PARENT_MODE_STATE)
        }
    }

    private fun Preferences.toParentModeSession(): ParentModeSession? {
        val startedAtMillis = this[PreferencesKey.PARENT_MODE_STARTED_AT] ?: return null
        val expiresAtMillis = this[PreferencesKey.PARENT_MODE_EXPIRES_AT] ?: return null
        val durationMinutes = this[PreferencesKey.PARENT_MODE_DURATION_MINUTES] ?: return null
        val allowedApps = this[PreferencesKey.PARENT_MODE_ALLOWED_APPS] ?: return null
        val state = this[PreferencesKey.PARENT_MODE_STATE]?.toParentModeSessionState() ?: return null

        return ParentModeSession(
            startedAtMillis = startedAtMillis,
            expiresAtMillis = expiresAtMillis,
            durationMinutes = durationMinutes,
            allowedApps = allowedApps,
            state = state,
        )
    }

    private fun ParentModeSessionState.toStoredValue(): String = when (this) {
        ParentModeSessionState.Setup -> "setup"
        ParentModeSessionState.Active -> "active"
        ParentModeSessionState.Expired -> "expired"
        ParentModeSessionState.UnlockedByPin -> "unlocked_by_pin"
        ParentModeSessionState.Cancelled -> "cancelled"
    }

    private fun String.toParentModeSessionState(): ParentModeSessionState? = when (this) {
        "setup" -> ParentModeSessionState.Setup
        "active" -> ParentModeSessionState.Active
        "expired" -> ParentModeSessionState.Expired
        "unlocked_by_pin" -> ParentModeSessionState.UnlockedByPin
        "cancelled" -> ParentModeSessionState.Cancelled
        else -> null
    }
}
