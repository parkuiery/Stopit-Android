package com.uiery.keep.feature.onboarding.proposal

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.KeepCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.keep.feature.onboarding.OnboardingBottomActionBar
import com.uiery.kds.KeepButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.uiery.kds.KeepInputButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.KeepTimeInput
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
import com.uiery.keep.ui.component.withoutBottomInset

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
            modifier = Modifier.fillMaxSize().padding(insets.withoutBottomInset()),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
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
                    val loadingDescription = stringResource(R.string.first_promise_accessibility_loading)
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KeepCircularProgressIndicator(
                            modifier = Modifier.semantics {
                                contentDescription = loadingDescription
                                stateDescription = loadingDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                            color = KeepTheme.colors.primary,
                        )
                    }
                } else {
                ProposalCard {
                    val factCopy = when (proposalFactCopyVariant(state.factType, state.usageCoverageDays)) {
                        ProposalFactCopyVariant.AverageSevenDays -> stringResource(
                            R.string.first_promise_usage_fact,
                            state.appLabel,
                            formatAverageUsageDuration(state.averageDailyMinutes ?: 0),
                        )
                        ProposalFactCopyVariant.AveragePartialCoverage -> stringResource(
                            R.string.first_promise_usage_fact_partial_average,
                            state.appLabel,
                            state.usageCoverageDays,
                            formatAverageUsageDuration(state.averageDailyMinutes ?: 0),
                        )
                        ProposalFactCopyVariant.CoverageSevenDays -> stringResource(
                            R.string.first_promise_usage_fact_all_days,
                            state.appLabel,
                        )
                        ProposalFactCopyVariant.CoveragePartial -> stringResource(
                                R.string.first_promise_usage_fact_covered_days,
                                state.appLabel,
                                state.usageCoverageDays,
                            )
                        ProposalFactCopyVariant.Neutral -> stringResource(
                            R.string.first_promise_usage_fact_neutral,
                            state.appLabel,
                        )
                    }
                    Text(
                        text = factCopy,
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
                    if (shouldShowUsageEstimateNote(state.factType)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.first_promise_recent_usage_note),
                            color = KeepTheme.colors.surfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
                }
                Spacer(Modifier.height(12.dp))
                ProposalEditActions(
                    timeValue = formatTime(state.startMinutes),
                    daysValue = state.repeatDays.sorted().joinToString(" · ") {
                        formatWeekdayShort(DayOfWeek.of(it), Locale.getDefault())
                    },
                    appValue = state.appLabel,
                    onEdit = viewModel::showPicker,
                )
                Spacer(Modifier.height(20.dp))
                }
            }
            OnboardingBottomActionBar {
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.first_promise_start),
                    enabled = state.canStart,
                    bottomSpacing = false,
                    onClick = viewModel::startFirstPromise,
                )
            }
        }
    }
}

@Composable
private fun formatAverageUsageDuration(totalMinutes: Long): String {
    val safeTotalMinutes = totalMinutes.coerceAtLeast(0)
    val hours = safeTotalMinutes / 60
    val minutes = safeTotalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(
            R.string.focus_summary_share_duration_hours_minutes,
            pluralStringResource(
                R.plurals.focus_summary_share_duration_hour,
                hours.toInt(),
                hours,
            ),
            pluralStringResource(
                R.plurals.focus_summary_share_duration_minute,
                minutes.toInt(),
                minutes,
            ),
        )
        hours > 0 -> pluralStringResource(
            R.plurals.focus_summary_share_duration_hour,
            hours.toInt(),
            hours,
        )
        else -> pluralStringResource(
            R.plurals.focus_summary_share_duration_minute,
            minutes.toInt(),
            minutes,
        )
    }
}

/**
 * 제안된 약속의 각 항목을 그 값 위에서 직접 고치게 한다.
 *
 * 3분할 가로 배치는 한 칸이 약 98dp까지 좁아져 "시간 바꾸기" 같은 라벨이 접히고, 고정 높이
 * 버튼 안에서 잘렸다. 전폭 행으로 세우면 잘림이 구조적으로 사라지고, 현재 값을 같은 행에서
 * 보여줄 수 있어 무엇을 바꾸는지 대조 없이 알 수 있다.
 */
@Composable
internal fun ProposalEditActions(
    timeValue: String,
    daysValue: String,
    appValue: String,
    onEdit: (ProposalPicker) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProposalEditRow(
            label = stringResource(R.string.first_promise_change_time),
            value = timeValue,
            onClick = { onEdit(ProposalPicker.StartTime) },
        )
        ProposalEditRow(
            label = stringResource(R.string.first_promise_change_days),
            value = daysValue,
            onClick = { onEdit(ProposalPicker.RepeatDays) },
        )
        ProposalEditRow(
            label = stringResource(R.string.first_promise_change_app),
            value = appValue,
            onClick = { onEdit(ProposalPicker.App) },
        )
    }
}

@Composable
private fun ProposalEditRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    KeepInputButton(
        placeholder = label,
        value = value,
        onClick = onClick,
        leadingContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.round_arrow_forward_ios_24),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

internal enum class ProposalFactCopyVariant {
    AverageSevenDays,
    AveragePartialCoverage,
    CoverageSevenDays,
    CoveragePartial,
    Neutral,
}

internal fun proposalFactCopyVariant(
    factType: ProposalFactType,
    usageCoverageDays: Int,
): ProposalFactCopyVariant = when (factType) {
    ProposalFactType.Average -> if (usageCoverageDays == 7) {
        ProposalFactCopyVariant.AverageSevenDays
    } else {
        ProposalFactCopyVariant.AveragePartialCoverage
    }
    ProposalFactType.Coverage -> if (usageCoverageDays == 7) {
        ProposalFactCopyVariant.CoverageSevenDays
    } else {
        ProposalFactCopyVariant.CoveragePartial
    }
    ProposalFactType.Neutral -> ProposalFactCopyVariant.Neutral
}

internal fun shouldShowUsageEstimateNote(factType: ProposalFactType): Boolean =
    factType == ProposalFactType.Average || factType == ProposalFactType.Coverage

@Composable
private fun ProposalCard(content: @Composable ColumnScope.() -> Unit) {
    KeepCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        bordered = true,
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
        KeepTimeInput(state = pickerState)
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
                KeepCard(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .toggleable(isSelected, role = Role.Checkbox) {
                            val next = if (day.value in pending) pending - day.value else pending + day.value
                            if (next.isNotEmpty()) pending = next
                        },
                    variant = if (isSelected) KeepCardVariant.BrandSolid else KeepCardVariant.LayerDefault,
                    bordered = !isSelected,
                ) {
                    Column(
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = formatWeekdayShort(day, Locale.getDefault()),
                            color = if (isSelected) {
                                KeepTheme.semanticColors.foreground.onBrand
                            } else {
                                KeepTheme.colors.onSurfaceVariant
                            },
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
