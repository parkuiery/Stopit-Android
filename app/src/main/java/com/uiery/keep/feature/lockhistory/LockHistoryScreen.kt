package com.uiery.keep.feature.lockhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.component.LockHistorySessionItem
import com.uiery.keep.feature.lockhistory.component.LockHistorySummaryCard
import com.uiery.keep.feature.lockhistory.component.LockHistoryTab
import com.uiery.keep.feature.lockhistory.component.LockHistoryTopApps
import com.uiery.keep.feature.lockhistory.component.LockHistoryWeekCalendar
import com.uiery.keep.model.LockHistoryModel
import com.uiery.keep.util.formatLockHistoryDateHeader
import com.uiery.keep.util.formatMonthDay
import com.uiery.keep.util.formatYearMonth
import org.orbitmvi.orbit.compose.collectAsState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: LockHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateBlockedApps: () -> Unit,
) {
    val uiState by viewModel.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lock_history_title),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                )
            )
        }
    ) { paddingValues ->
        // 선택된 날짜에 따른 세션 필터링
        val sessionsToShow: Map<LocalDate, List<LockHistoryModel>> =
            uiState.selectedDate?.let { selectedDate ->
                uiState.groupedSessions[selectedDate]?.let { sessions ->
                    mapOf(selectedDate to sessions)
                } ?: emptyMap()
            } ?: uiState.groupedSessions

        // 선택된 날짜에 따른 통계 계산
        val displaySessions = sessionsToShow.values.flatten()
        val displayTotalDuration = displaySessions.sumOf { it.durationMillis }
        val displaySessionCount = displaySessions.size
        val displayTopApps = displaySessions
            .flatMap { it.lockedApps }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
        ) {
            LockHistoryTab(
                selectedPeriod = uiState.periodType,
                onSelectPeriod = viewModel::selectPeriodType,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PeriodSelector(
                periodType = uiState.periodType,
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onPreviousPeriod = viewModel::moveToPreviousPeriod,
                onNextPeriod = viewModel::moveToNextPeriod,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LockHistorySummaryCard(
                totalDuration = displayTotalDuration,
                sessionCount = displaySessionCount,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.periodType == PeriodType.WEEK) {
                LockHistoryWeekCalendar(
                    startDate = uiState.startDate,
                    endDate = uiState.endDate,
                    durationByDate = uiState.durationByDate,
                    selectedDate = uiState.selectedDate,
                    onSelectDate = viewModel::selectDate,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (displayTopApps.isNotEmpty()) {
                LockHistoryTopApps(
                    topApps = displayTopApps,
                    onClick = onNavigateBlockedApps,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (sessionsToShow.isEmpty()) {
                Text(
                    text = stringResource(R.string.lock_history_no_records),
                    color = KeepTheme.colors.onTertiaryContainer,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sessionsToShow.toSortedMap(compareByDescending { it }).forEach { (date, sessions) ->
                        item(key = date.toString()) {
                            DateHeader(date = date)
                        }
                        items(
                            items = sessions,
                            key = { it.id }
                        ) { session ->
                            LockHistorySessionItem(session = session)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    modifier: Modifier = Modifier,
    periodType: PeriodType,
    startDate: LocalDate,
    endDate: LocalDate,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousPeriod) {
            Icon(
                painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                contentDescription = null,
                tint = KeepTheme.colors.onSurfaceVariant,
            )
        }
        Text(
            text = when (periodType) {
                PeriodType.WEEK -> "${formatMonthDay(startDate)} - ${formatMonthDay(endDate)}"
                PeriodType.MONTH -> formatYearMonth(startDate)
            },
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNextPeriod) {
            Icon(
                painter = painterResource(R.drawable.round_arrow_forward_ios_24),
                contentDescription = null,
                tint = KeepTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateHeader(
    modifier: Modifier = Modifier,
    date: LocalDate,
) {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
    Text(
        modifier = modifier.padding(vertical = 4.dp),
        text = formatLockHistoryDateHeader(date = date, locale = locale),
        color = KeepTheme.colors.onTertiaryContainer,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
