package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineStoreTest {

    @Test
    fun readCachedRoutinesReturnsEmptyListWhenCacheMissing() = runBlocking {
        val dataStore = FakePreferencesDataStore()

        val routines = RoutineStore(dataStore).readCachedRoutines()

        assertEquals(emptyList<RoutineModel>(), routines)
    }

    @Test
    fun writeCachedRoutinesPersistsAndReadsBackRoutines() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val routineStore = RoutineStore(dataStore)
        val expected = listOf(
            routine(id = 1L, name = "Morning focus", isEnabled = true),
            routine(id = 2L, name = "Evening focus", isEnabled = false),
        )

        routineStore.writeCachedRoutines(expected)

        assertEquals(expected, routineStore.readCachedRoutines())
    }

    @Test
    fun readCachedRoutinesReturnsEmptyListWhenCacheMalformed() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        dataStore.edit { preferences ->
            preferences[PreferencesKey.ROUTINES] = "not-json"
        }

        val routines = RoutineStore(dataStore).readCachedRoutines()

        assertEquals(emptyList<RoutineModel>(), routines)
    }

    @Test
    fun writeCachedRoutinesUsesKeepDataStoreKeyInsteadOfSeparateRoutineDataStore() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val routineStore = RoutineStore(dataStore)
        val expected = listOf(routine(id = 7L, name = "Keep using same datastore", isEnabled = true))

        routineStore.writeCachedRoutines(expected)

        assertEquals(true, dataStore.snapshot().contains(PreferencesKey.ROUTINES))
        assertEquals(false, dataStore.snapshot().contains(stringPreferencesKey("shadow_routines")))
    }

    private fun routine(
        id: Long,
        name: String,
        isEnabled: Boolean,
    ) = RoutineModel(
        id = id,
        name = name,
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 10, minute = 0),
        repeatDays = "1000000",
        lockApplications = listOf("com.example.blocked"),
        isEnabled = isEnabled,
        changeLockHours = null,
    )
}

private class FakePreferencesDataStore(
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
