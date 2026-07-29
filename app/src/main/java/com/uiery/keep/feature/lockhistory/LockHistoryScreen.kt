package com.uiery.keep.feature.lockhistory

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.uiery.kds.KeepCenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.uiery.kds.KeepIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.component.LockHistorySessionItem
import com.uiery.keep.feature.lockhistory.component.LockHistorySummaryCard
import com.uiery.keep.feature.lockhistory.component.LockHistoryTab
import com.uiery.keep.feature.lockhistory.component.LockHistoryTopApps
import com.uiery.keep.feature.lockhistory.component.LockHistoryWeekCalendar
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.ui.component.RepeatBlockRoutineSuggestionCard
import com.uiery.keep.util.formatLockHistoryDateHeader
import com.uiery.keep.util.formatMonthDay
import com.uiery.keep.util.formatYearMonth
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: LockHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateBlockedApps: () -> Unit,
    onNavigateRoutineWithRepeatBlockPrefill: (RepeatBlockRoutineSuggestion) -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val context = LocalContext.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is LockHistorySideEffect.ShareFocusSummary -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, effect.payload.text)
                }
                val chooser = Intent.createChooser(
                    sendIntent,
                    context.getString(R.string.lock_history_focus_share_sheet_title),
                )
                try {
                    context.startActivity(chooser)
                    viewModel.onFocusSummaryShareSheetOpened(effect.payload)
                } catch (_: ActivityNotFoundException) {
                    viewModel.onFocusSummaryShareFailed(effect.payload)
                }
            }

            is LockHistorySideEffect.NavigateToRoutineWithRepeatBlockPrefill -> {
                onNavigateRoutineWithRepeatBlockPrefill(effect.suggestion)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
        topBar = {
            KeepCenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lock_history_title),
                        color = KeepTheme.semanticColors.foreground.neutral,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    KeepIconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = KeepTheme.semanticColors.foreground.neutral,
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        val displayReport = buildLockHistoryDisplayReport(
            groupedSessions = uiState.groupedSessions,
            selectedDate = uiState.selectedDate,
            periodType = uiState.periodType,
            fallbackReport = uiState.performanceReport,
        )

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
                totalDuration = displayReport.totalDurationMillis,
                sessionCount = displayReport.sessionCount,
                report = displayReport.performanceReport,
            )

            uiState.focusSummarySharePayload?.let {
                Spacer(modifier = Modifier.height(12.dp))
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.lock_history_focus_share_button),
                    onClick = viewModel::shareFocusSummary,
                )
            }

            uiState.repeatBlockRoutineSuggestion?.let { suggestion ->
                Spacer(modifier = Modifier.height(12.dp))
                RepeatBlockRoutineSuggestionCard(
                    modifier = Modifier.fillMaxWidth(),
                    suggestion = suggestion,
                    titleResId = R.string.repeat_block_suggestion_lock_history_title,
                    messageResId = R.string.repeat_block_suggestion_lock_history_message,
                    onApplyClick = viewModel::openRepeatBlockRoutineSuggestion,
                    onDismissClick = viewModel::dismissRepeatBlockRoutineSuggestion,
                )
            }

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

            if (displayReport.performanceReport.shouldShowTopApps) {
                LockHistoryTopApps(
                    topApps = displayReport.topApps,
                    report = displayReport.performanceReport,
                    onClick = onNavigateBlockedApps,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (displayReport.sessionsToShow.isEmpty()) {
                Text(
                    text = stringResource(displayReport.performanceReport.topAppsSupportingResId),
                    color = KeepTheme.semanticColors.foreground.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    displayReport.sessionsToShow.toSortedMap(compareByDescending { it }).forEach { (date, sessions) ->
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
        KeepIconButton(onClick = onPreviousPeriod) {
            Icon(
                painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                contentDescription = stringResource(R.string.cd_previous_period),
                tint = KeepTheme.semanticColors.foreground.neutral,
            )
        }
        Text(
            text = when (periodType) {
                PeriodType.WEEK -> "${formatMonthDay(startDate)} - ${formatMonthDay(endDate)}"
                PeriodType.MONTH -> formatYearMonth(startDate)
            },
            color = KeepTheme.semanticColors.foreground.neutral,
            style = MaterialTheme.typography.titleSmall,
        )
        KeepIconButton(onClick = onNextPeriod) {
            Icon(
                painter = painterResource(R.drawable.round_arrow_forward_ios_24),
                contentDescription = stringResource(R.string.cd_next_period),
                tint = KeepTheme.semanticColors.foreground.neutral,
            )
        }
    }
}

@Composable
private fun DateHeader(
    modifier: Modifier = Modifier,
    date: LocalDate,
) {
    val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    Text(
        modifier = modifier.padding(vertical = 4.dp),
        text = formatLockHistoryDateHeader(date = date, locale = locale),
        color = KeepTheme.semanticColors.foreground.muted,
        style = MaterialTheme.typography.labelMedium,
    )
}
