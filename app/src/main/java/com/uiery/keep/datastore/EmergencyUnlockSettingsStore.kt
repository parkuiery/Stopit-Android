package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.service.ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.sanitizeEmergencyUnlockDailyLimit
import com.uiery.keep.service.sanitizeEmergencyUnlockDurationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access boundary for emergency-unlock settings stored in keep-datastore.
 *
 * Preference keys stay unchanged for backwards compatibility. UI and service code should use this
 * store so default/sanitize rules cannot drift between settings screens and unlock orchestration.
 */
@Singleton
class EmergencyUnlockSettingsStore
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
    ) {
        val settings: Flow<EmergencyUnlockSettingsSnapshot> =
            dataStore.data.map { preferences -> preferences.toEmergencyUnlockSettingsSnapshot() }

        suspend fun readSettings(): EmergencyUnlockSettingsSnapshot = settings.first()

        suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = enabled
            }
        }

        suspend fun setDailyLimit(limit: Int) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = sanitizeEmergencyUnlockDailyLimit(limit)
            }
        }

        suspend fun toggleDuration(minutes: Int) {
            if (minutes !in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS) return
            dataStore.edit { preferences ->
                val current = sanitizeEmergencyUnlockDurationOptions(
                    preferences[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS],
                ).toSet()
                val next =
                    if (minutes in current) {
                        if (current.size == 1) current else current - minutes
                    } else {
                        current + minutes
                    }
                preferences[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = next.map { it.toString() }.toSet()
            }
        }

        suspend fun setReasonRequired(required: Boolean) {
            dataStore.edit { preferences ->
                preferences[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = required
            }
        }

        private fun Preferences.toEmergencyUnlockSettingsSnapshot(): EmergencyUnlockSettingsSnapshot =
            EmergencyUnlockSettingsSnapshot(
                enabled = this[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] ?: true,
                dailyLimit = sanitizeEmergencyUnlockDailyLimit(
                    this[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT],
                ),
                durationOptions = sanitizeEmergencyUnlockDurationOptions(
                    this[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS],
                ),
                reasonRequired = this[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] ?: true,
            )
    }

data class EmergencyUnlockSettingsSnapshot(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val reasonRequired: Boolean = true,
)
