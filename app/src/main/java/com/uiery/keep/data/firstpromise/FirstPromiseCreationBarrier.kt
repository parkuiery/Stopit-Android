package com.uiery.keep.data.firstpromise

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface FirstPromiseCreationBarrier {
    suspend fun isComplete(draftId: String): Boolean
    suspend fun markComplete(draftId: String)
}

@Singleton
class FirstPromiseCreationBarrierStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) : FirstPromiseCreationBarrier {
    override suspend fun isComplete(draftId: String): Boolean =
        draftId in dataStore.data.first()[PreferencesKey.FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS].orEmpty()

    override suspend fun markComplete(draftId: String) {
        dataStore.edit { preferences ->
            val completedDraftIds =
                preferences[PreferencesKey.FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS].orEmpty()
            if (draftId !in completedDraftIds) {
                preferences[PreferencesKey.FIRST_PROMISE_CREATION_BARRIER_DRAFT_IDS] = completedDraftIds + draftId
            }
        }
    }
}

internal class InMemoryFirstPromiseCreationBarrier : FirstPromiseCreationBarrier {
    private val completedDraftIds = mutableSetOf<String>()

    override suspend fun isComplete(draftId: String): Boolean = draftId in completedDraftIds

    override suspend fun markComplete(draftId: String) {
        completedDraftIds += draftId
    }
}
