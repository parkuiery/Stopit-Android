package com.uiery.keep.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.feature.lockhistory.LockHistoryRepository
import com.uiery.keep.model.LockHistoryModel
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull

internal data class LockHistoryLedgerSummary(
    val groupedSessions: Map<LocalDate, List<LockHistoryModel>>,
    val totalDurationMillis: Long,
    val longestDurationMillis: Long,
    val sessionCount: Int,
    val topApps: List<String>,
    val durationByDate: Map<LocalDate, Long>,
)

/**
 * Room lock history is the authoritative ledger for summary/detail history surfaces.
 *
 * `LONG_BLOCK_TIME` and `TOTAL_BLOCK_TIME` remain as a legacy compatibility cache for older
 * installs, but new UI aggregates should prefer [summarizeLockHistoryLedger].
 */
internal fun summarizeLockHistoryLedger(sessions: List<LockHistoryModel>): LockHistoryLedgerSummary {
    val groupedSessions = sessions.groupBy { it.date }
    val totalDuration = sessions.sumOf { it.durationMillis }
    val longestDuration = sessions.maxOfOrNull { it.durationMillis } ?: 0L
    val topApps = sessions
        .flatMap { it.lockedApps }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .map { it.key }
    val durationByDate = groupedSessions.mapValues { (_, dailySessions) ->
        dailySessions.sumOf { it.durationMillis }
    }

    return LockHistoryLedgerSummary(
        groupedSessions = groupedSessions,
        totalDurationMillis = totalDuration,
        longestDurationMillis = longestDuration,
        sessionCount = sessions.size,
        topApps = topApps,
        durationByDate = durationByDate,
    )
}

internal suspend fun recordLockHistorySession(
    dataStore: DataStore<Preferences>,
    lockHistoryRepository: LockHistoryRepository,
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

    lockHistoryRepository.recordSession(
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationMillis = durationMillis,
        lockedApps = lockedApps,
        isRoutine = isRoutine,
    )
}
