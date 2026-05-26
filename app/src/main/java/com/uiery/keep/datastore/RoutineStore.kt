package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.receiver.RoutineReceiverPolicy
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Typed wrapper around the legacy routine compatibility cache stored in keep-datastore.
 *
 * Room remains the authoritative source of truth. This wrapper only centralizes the
 * compatibility cache so receivers/viewmodels stop touching PreferencesKey.ROUTINES directly.
 */
class RoutineStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun readCachedRoutines(): List<RoutineModel> {
        val preferences = dataStore.data.first()
        return RoutineReceiverPolicy.decodeStoredRoutines(preferences[PreferencesKey.ROUTINES])
    }

    suspend fun writeCachedRoutines(routines: List<RoutineModel>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.ROUTINES] = Json.encodeToString(routines)
        }
    }
}
