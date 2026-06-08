package com.uiery.keep.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.repository.LockHistorySessionWriter
import com.uiery.keep.datastore.PreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Records lock-history sessions to the authoritative Room ledger while maintaining legacy
 * DataStore summary counters as an internal compatibility cache.
 */
class LockHistoryRecorder @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val lockHistorySessionWriter: LockHistorySessionWriter,
) {
    suspend fun recordSession(
        startTimestamp: Long,
        endTimestamp: Long,
        lockedApps: Collection<String>,
        isRoutine: Boolean,
    ) {
        val durationMillis = (endTimestamp - startTimestamp).coerceAtLeast(0L)
        val preferences = dataStore.data.firstOrNull()
        val previousLongest = preferences?.get(PreferencesKey.LONG_BLOCK_TIME) ?: 0L
        val previousTotal = preferences?.get(PreferencesKey.TOTAL_BLOCK_TIME) ?: 0L

        dataStore.edit { mutablePreferences ->
            mutablePreferences[PreferencesKey.LONG_BLOCK_TIME] = maxOf(previousLongest, durationMillis)
            mutablePreferences[PreferencesKey.TOTAL_BLOCK_TIME] = previousTotal + durationMillis
        }

        lockHistorySessionWriter.recordSession(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            durationMillis = durationMillis,
            lockedApps = lockedApps,
            isRoutine = isRoutine,
        )
    }
}
