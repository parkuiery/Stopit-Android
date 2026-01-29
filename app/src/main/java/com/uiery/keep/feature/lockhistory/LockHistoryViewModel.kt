package com.uiery.keep.feature.lockhistory

import androidx.lifecycle.ViewModel
import com.uiery.keep.database.dao.LockHistoryDao
import com.uiery.keep.model.LockHistoryModel
import com.uiery.keep.model.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class PeriodType {
    WEEK, MONTH
}

@HiltViewModel
class LockHistoryViewModel @Inject constructor(
    private val lockHistoryDao: LockHistoryDao,
) : ContainerHost<LockHistoryUiState, LockHistorySideEffect>, ViewModel() {

    override val container: Container<LockHistoryUiState, LockHistorySideEffect> =
        container(LockHistoryUiState())

    init {
        loadHistory()
    }

    internal fun selectPeriodType(periodType: PeriodType) = intent {
        reduce { state.copy(periodType = periodType, currentDate = LocalDate.now()) }
        loadHistory()
    }

    internal fun moveToPreviousPeriod() = intent {
        val newDate = when (state.periodType) {
            PeriodType.WEEK -> state.currentDate.minusWeeks(1)
            PeriodType.MONTH -> state.currentDate.minusMonths(1)
        }
        reduce { state.copy(currentDate = newDate) }
        loadHistory()
    }

    internal fun moveToNextPeriod() = intent {
        val newDate = when (state.periodType) {
            PeriodType.WEEK -> state.currentDate.plusWeeks(1)
            PeriodType.MONTH -> state.currentDate.plusMonths(1)
        }
        reduce { state.copy(currentDate = newDate) }
        loadHistory()
    }

    internal fun selectDate(date: LocalDate) = intent {
        val newSelectedDate = if (state.selectedDate == date) null else date
        reduce { state.copy(selectedDate = newSelectedDate) }
    }

    private fun loadHistory() = intent {
        val (startDate, endDate) = getDateRange(state.periodType, state.currentDate)
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val sessions = lockHistoryDao.fetchByDateRange(startMillis, endMillis)
            .firstOrNull()
            ?.map { it.toModel() }
            ?: emptyList()

        val groupedSessions = sessions.groupBy { it.date }
        val totalDuration = sessions.sumOf { it.durationMillis }
        val sessionCount = sessions.size

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

        reduce {
            state.copy(
                startDate = startDate,
                endDate = endDate,
                groupedSessions = groupedSessions,
                totalDuration = totalDuration,
                sessionCount = sessionCount,
                topApps = topApps,
                durationByDate = durationByDate,
                selectedDate = null,
            )
        }
    }

    private fun getDateRange(periodType: PeriodType, currentDate: LocalDate): Pair<LocalDate, LocalDate> {
        return when (periodType) {
            PeriodType.WEEK -> {
                val startOfWeek = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfWeek = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                startOfWeek to endOfWeek
            }
            PeriodType.MONTH -> {
                val startOfMonth = currentDate.withDayOfMonth(1)
                val endOfMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth())
                startOfMonth to endOfMonth
            }
        }
    }
}

data class LockHistoryUiState(
    val periodType: PeriodType = PeriodType.WEEK,
    val currentDate: LocalDate = LocalDate.now(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val groupedSessions: Map<LocalDate, List<LockHistoryModel>> = emptyMap(),
    val totalDuration: Long = 0L,
    val sessionCount: Int = 0,
    val topApps: List<String> = emptyList(),
    val durationByDate: Map<LocalDate, Long> = emptyMap(),
    val selectedDate: LocalDate? = null,
)

sealed class LockHistorySideEffect
