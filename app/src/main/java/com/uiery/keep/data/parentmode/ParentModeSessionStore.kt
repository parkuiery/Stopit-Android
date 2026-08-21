package com.uiery.keep.data.parentmode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.parentmode.ParentModeGuardianPinDigest
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
            val guardianPin = session.guardianPin
            if (guardianPin == null) {
                preferences.remove(PreferencesKey.PARENT_MODE_PIN_HASH)
                preferences.remove(PreferencesKey.PARENT_MODE_PIN_SALT)
            } else {
                preferences[PreferencesKey.PARENT_MODE_PIN_HASH] = guardianPin.hash
                preferences[PreferencesKey.PARENT_MODE_PIN_SALT] = guardianPin.salt
            }
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
            preferences.remove(PreferencesKey.PARENT_MODE_PIN_HASH)
            preferences.remove(PreferencesKey.PARENT_MODE_PIN_SALT)
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
            guardianPin = storedGuardianPin(),
        )
    }

    /**
     * Absent for a session that was already running when the PIN started being stored. The session
     * itself still has to load — dropping it would unlock the child's phone mid-session on update —
     * so the missing digest travels with it and the PIN gate handles it.
     */
    private fun Preferences.storedGuardianPin(): ParentModeGuardianPinDigest? {
        val hash = this[PreferencesKey.PARENT_MODE_PIN_HASH] ?: return null
        val salt = this[PreferencesKey.PARENT_MODE_PIN_SALT] ?: return null
        return ParentModeGuardianPinDigest(hash = hash, salt = salt)
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
