package com.uiery.keep.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.routineStore: DataStore<Preferences> by preferencesDataStore(name = "routine-datastore")

object RoutinePreferencesKey {

}