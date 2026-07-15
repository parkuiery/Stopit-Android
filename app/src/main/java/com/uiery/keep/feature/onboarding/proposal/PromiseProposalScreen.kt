package com.uiery.keep.feature.onboarding.proposal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.ui.component.AppSelectionMode
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.util.formatWeekdayShort
import java.time.DayOfWeek
import java.util.Locale
import java.util.Calendar
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromiseProposalScreen(
    onNavigateAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PromiseProposalViewModel = hiltViewModel(),
) {
    val state by viewModel.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    viewModel.collectSideEffect { effect ->
        if (effect == PromiseProposalSideEffect.NavigateAccessibility) onNavigateAccessibility()
    }
    LaunchedEffect(viewModel) { viewModel.onStepViewed() }

    if (state.isPickerVisible) {
        KeepModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = viewModel::hidePicker,
        ) {
            when (state.visiblePicker) {
                ProposalPicker.App -> CategoryBottomSheetContent(
                    storeSelectApps = viewModel.selectedAppPackageName()?.let(::setOf).orEmpty(),
                    selectionMode = AppSelectionMode.Single,
                    onComplete = {},
                    onSingleComplete = viewModel::changeApp,
                )
                ProposalPicker.StartTime -> StartTimePicker(state.startMinutes, viewModel::changeStartMinutes)
                ProposalPicker.RepeatDays -> RepeatDaysPicker(state.repeatDays, viewModel::changeRepeatDays)
                ProposalPicker.None -> Unit
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = KeepTheme.colors.background) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    modifier = Modifier.padding(top = 36.dp).semantics { heading() },
                    text = stringResource(R.string.first_promise_proposal_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = KeepTheme.colors.primary)
                    }
                } else {
                ProposalCard {
                    Text(
                        text = when (state.factType) {
                            ProposalFactType.Average -> stringResource(
                                R.string.first_promise_usage_fact,
                                state.appLabel,
                                state.averageDailyMinutes ?: 0,
                            )
                            ProposalFactType.Coverage -> if (state.usageCoverageDays == 7) {
                                stringResource(R.string.first_promise_usage_fact_all_days, state.appLabel)
                            } else {
                                stringResource(
                                R.string.first_promise_usage_fact_covered_days,
                                state.appLabel,
                                state.usageCoverageDays,
                            )
                            }
                            ProposalFactType.Neutral -> stringResource(
                                R.string.first_promise_usage_fact_neutral,
                                state.appLabel,
                            )
                        },
                        color = KeepTheme.colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.factType != ProposalFactType.Neutral) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = patternCopy(state.patternType),
                            color = KeepTheme.colors.surfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.first_promise_recent_usage_note),
                        color = KeepTheme.colors.surfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ProposalCard {
                    Text(
                        text = stringResource(
                            R.string.first_promise_action_summary,
                            formatTime(state.startMinutes),
                            state.appLabel,
                        ),
                        color = KeepTheme.colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.repeatDays.sorted().joinToString(" · ") {
                            formatWeekdayShort(DayOfWeek.of(it), Locale.getDefault())
                        },
                        color = KeepTheme.colors.surfaceVariant,
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.showPicker(ProposalPicker.StartTime) },
                    ) {
                        Text(stringResource(R.string.first_promise_change_time), color = KeepTheme.colors.primary)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.showPicker(ProposalPicker.RepeatDays) },
                    ) {
                        Text(stringResource(R.string.first_promise_change_days), color = KeepTheme.colors.primary)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.showPicker(ProposalPicker.App) },
                    ) {
                        Text(stringResource(R.string.first_promise_change_app), color = KeepTheme.colors.primary)
                    }
                }
                Spacer(Modifier.height(20.dp))
                }
            }
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.first_promise_start),
                enabled = state.canStart,
                onClick = viewModel::startFirstPromise,
            )
        }
    }
}

@Composable
private fun ProposalCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        border = BorderStroke(1.dp, KeepTheme.colors.tertiary),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun patternCopy(pattern: UsagePatternType): String = stringResource(
    when (pattern) {
        UsagePatternType.Night -> R.string.first_promise_pattern_night
        UsagePatternType.PeakWindow -> R.string.first_promise_pattern_peak
        UsagePatternType.TopApp -> R.string.first_promise_pattern_top_app
        UsagePatternType.Manual -> R.string.first_promise_pattern_manual
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StartTimePicker(startMinutes: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = startMinutes / 60,
        initialMinute = startMinutes % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.first_promise_time_picker_title),
            style = MaterialTheme.typography.titleLarge,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TimeInput(state = pickerState)
        Spacer(Modifier.height(24.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.first_promise_edit_confirm),
            bottomSpacing = false,
            onClick = { onChange(pickerState.hour * 60 + pickerState.minute) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RepeatDaysPicker(selected: Set<Int>, onConfirm: (Set<Int>) -> Unit) {
    var pending by remember(selected) { mutableStateOf(selected) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.first_promise_repeat_picker_title),
            style = MaterialTheme.typography.titleLarge,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val isSelected = day.value in pending
                Card(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .toggleable(isSelected, role = Role.Checkbox) {
                            val next = if (day.value in pending) pending - day.value else pending + day.value
                            if (next.isNotEmpty()) pending = next
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) KeepTheme.colors.primary else KeepTheme.colors.onSecondary,
                    ),
                ) {
                    Column(
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = formatWeekdayShort(day, Locale.getDefault()),
                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        KeepButton(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            text = stringResource(R.string.first_promise_edit_confirm),
            bottomSpacing = false,
            onClick = { onConfirm(pending) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun formatTime(minutes: Int): String {
    val context = LocalContext.current
    val formatter = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val calendar = remember(minutes) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
    }
    return formatter.format(calendar.time)
}
