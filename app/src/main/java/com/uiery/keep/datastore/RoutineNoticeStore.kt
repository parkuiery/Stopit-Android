package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.receiver.PendingRoutineStartNotice
import com.uiery.keep.receiver.RoutineReceiverPolicy
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed boundary for routine notification handoff state stored in keep-datastore.
 *
 * These keys are reset-only, device/runtime UI handoff state rather than routine source-of-truth.
 * Keep receiver, home, and routine UI code behind this store so queue encoding/draining and alarm
 * permission prompt resets do not drift across call sites.
 */
@Singleton
class RoutineNoticeStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    suspend fun readPendingRoutineStartNoticeMessages(): List<String> {
        val preferences = dataStore.data.first()
        return RoutineReceiverPolicy.decodePendingRoutineStartNotices(
            preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
        )
    }

    suspend fun enqueuePendingRoutineStartNotice(notice: PendingRoutineStartNotice) {
        dataStore.edit { preferences ->
            val encodedNotices = RoutineReceiverPolicy.enqueuePendingRoutineStartNotice(
                storedValue = preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
                notice = notice,
            )
            if (encodedNotices == null) {
                preferences.remove(PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE)
            } else {
                preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE] = encodedNotices
            }
        }
    }

    suspend fun drainNextPendingRoutineStartNotice(): String? {
        val preferences = dataStore.data.first()
        val drain = RoutineReceiverPolicy.drainNextPendingRoutineStartNotice(
            preferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE],
        )
        val message = drain.message ?: return null

        dataStore.edit { editablePreferences ->
            val remainingStoredValue = drain.remainingStoredValue
            if (remainingStoredValue == null) {
                editablePreferences.remove(PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE)
            } else {
                editablePreferences[PreferencesKey.PENDING_ROUTINE_START_NOTICE_MESSAGE] = remainingStoredValue
            }
        }
        return message
    }

    suspend fun hasShownAlarmPermissionPrompt(): Boolean {
        val preferences = dataStore.data.first()
        return preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] == true
    }

    suspend fun markAlarmPermissionPromptShown() {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] = true
        }
    }

    suspend fun resetAlarmPermissionPrompt() {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_SHOWN_ALARM_PERMISSION] = false
        }
    }
}
