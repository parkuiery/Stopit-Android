package com.uiery.keep.service

import com.uiery.keep.model.LockHistoryModel
import java.time.LocalDate

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
