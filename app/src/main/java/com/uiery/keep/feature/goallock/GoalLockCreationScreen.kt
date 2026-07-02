package com.uiery.keep.feature.goallock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.SetupAppRow
import com.uiery.keep.ui.component.SetupChip
import com.uiery.keep.ui.component.SetupGroupCard
import com.uiery.keep.ui.component.SetupHero
import com.uiery.keep.ui.component.SetupSecondaryButton
import com.uiery.keep.ui.component.SetupSectionCaption
import com.uiery.keep.ui.component.SetupSectionHeader
import com.uiery.keep.ui.component.SetupSelectableCard
import com.uiery.keep.ui.component.SetupStepper
import com.uiery.keep.ui.component.SetupTextField
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalLockCreationScreen(
    viewModel: GoalLockCreationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val appSelectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isAppSelectionSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSelectedAppsFromCurrentSelection()
    }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is GoalLockCreationSideEffect.Created -> onNavigateGoalLockDetail(effect.goalLockId)
        }
    }

    if (isAppSelectionSheetVisible) {
        KeepModalBottomSheet(
            sheetState = appSelectionSheetState,
            onDismissRequest = { isAppSelectionSheetVisible = false },
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = uiState.selectedApps,
                onComplete = { selectedApps ->
                    viewModel.setSelectedApps(selectedApps)
                    isAppSelectionSheetVisible = false
                },
            )
        }
    }

    val title = stringResource(id = R.string.goal_lock_creation_title)
    val navigateBackLabel = stringResource(id = R.string.cd_navigate_back)
    val presetExamGoalName = stringResource(id = R.string.goal_lock_creation_preset_exam)
    val presetSnsGoalName = stringResource(id = R.string.goal_lock_creation_preset_sns)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = navigateBackLabel,
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KeepTheme.colors.background),
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        GoalLockCreationContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            state = uiState,
            onGoalNameChange = viewModel::setGoalName,
            onPresetExam = { viewModel.setPresetGoalName(GoalLockPresetGoal.Exam, presetExamGoalName) },
            onPresetSns = { viewModel.setPresetGoalName(GoalLockPresetGoal.Sns, presetSnsGoalName) },
            onSelectSevenDays = { viewModel.setDateRange(LocalDate.now(), LocalDate.now().plusDays(6)) },
            onSelectFourteenDays = { viewModel.setDateRange(LocalDate.now(), LocalDate.now().plusDays(13)) },
            onSelectThirtyDays = { viewModel.setDateRange(LocalDate.now(), LocalDate.now().plusDays(29)) },
            onCustomDaysChange = { days -> viewModel.setCustomDurationDays(LocalDate.now(), days) },
            onEndDateChange = { endDate -> viewModel.setEndDateSelection(LocalDate.now(), endDate) },
            onSetAllDay = viewModel::setAllDayMode,
            onSetWeekdayEvening = {
                viewModel.setScheduledMode(
                    repeatDays = setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                    ),
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(23, 0),
                )
            },
            onReloadCurrentSelection = viewModel::loadSelectedAppsFromCurrentSelection,
            onSelectApps = { isAppSelectionSheetVisible = true },
            onRemoveSelectedApp = viewModel::removeSelectedApp,
            onCreate = {
                viewModel.createGoalLock()
            },
        )
    }
}

@Composable
internal fun GoalLockCreationContent(
    modifier: Modifier = Modifier,
    state: GoalLockCreationUiState,
    onGoalNameChange: (String) -> Unit,
    onPresetExam: () -> Unit,
    onPresetSns: () -> Unit,
    onSelectSevenDays: () -> Unit,
    onSelectFourteenDays: () -> Unit,
    onSelectThirtyDays: () -> Unit,
    onCustomDaysChange: (Int) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onSetAllDay: () -> Unit,
    onSetWeekdayEvening: () -> Unit,
    onReloadCurrentSelection: () -> Unit,
    onSelectApps: () -> Unit,
    onRemoveSelectedApp: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val appLabelMissing = stringResource(id = R.string.goal_lock_creation_app_label_missing)
    val selectedAppItems = buildGoalLockSelectedAppItems(
        selectedPackages = state.selectedApps,
        resolveLabel = { null },
        unknownAppDescription = appLabelMissing,
    )
    val durationRangeText = stringResource(
        id = R.string.goal_lock_creation_duration_range,
        state.startDate,
        state.endDate,
    )
    val currentLockModeText = when (val mode = state.lockMode) {
        GoalLockCreationLockMode.AllDay -> stringResource(id = R.string.goal_lock_creation_current_mode_all_day)
        is GoalLockCreationLockMode.Scheduled -> stringResource(
            id = R.string.goal_lock_creation_current_mode_scheduled,
            mode.repeatDays.size,
            mode.startTime,
            mode.endTime,
        )
    }
    val selectedAppsText = stringResource(
        id = R.string.goal_lock_creation_selected_apps_helper,
        state.selectedApps.size,
    )
    val creationAccessibilityDescription = buildGoalLockCreationAccessibilityDescription(
        goalName = state.goalName,
        durationRangeText = durationRangeText,
        lockModeText = currentLockModeText,
        selectedAppsText = selectedAppsText,
    )

    val totalDays = (ChronoUnit.DAYS.between(state.startDate, state.endDate) + 1)
        .coerceAtLeast(1)
        .toInt()
    val isAllDaySelected = state.lockMode is GoalLockCreationLockMode.AllDay
    val isScheduledSelected = state.lockMode is GoalLockCreationLockMode.Scheduled

    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showEndDatePicker) {
        EndDatePickerDialog(
            initialDate = state.endDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { picked ->
                showEndDatePicker = false
                onEndDateChange(picked)
            },
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        SetupHero(
            iconResId = R.drawable.ic_goal_lock,
            title = stringResource(id = R.string.goal_lock_creation_title),
            subtitle = stringResource(id = R.string.goal_lock_creation_hero_subtitle),
        )

        // Goal name
        SetupGroupCard(
            modifier = Modifier.semantics { contentDescription = creationAccessibilityDescription },
        ) {
            SetupSectionHeader(title = stringResource(id = R.string.goal_lock_creation_goal_name_label))
            Spacer(modifier = Modifier.height(12.dp))
            SetupTextField(
                value = state.goalName,
                onValueChange = onGoalNameChange,
                placeholder = stringResource(id = R.string.goal_lock_creation_goal_name_placeholder),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupChip(
                    label = stringResource(id = R.string.goal_lock_creation_preset_exam),
                    selected = state.goalName == stringResource(id = R.string.goal_lock_creation_preset_exam),
                    onClick = onPresetExam,
                )
                SetupChip(
                    label = stringResource(id = R.string.goal_lock_creation_preset_sns),
                    selected = state.goalName == stringResource(id = R.string.goal_lock_creation_preset_sns),
                    onClick = onPresetSns,
                )
            }
        }

        // Duration
        SetupGroupCard {
            SetupSectionHeader(
                title = stringResource(id = R.string.goal_lock_creation_duration_label),
                valueLabel = durationRangeText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupChip(
                    label = stringResource(id = R.string.goal_lock_creation_duration_7_days),
                    selected = totalDays == 7,
                    onClick = onSelectSevenDays,
                )
                SetupChip(
                    label = stringResource(id = R.string.goal_lock_creation_duration_14_days),
                    selected = totalDays == 14,
                    onClick = onSelectFourteenDays,
                )
                SetupChip(
                    label = stringResource(id = R.string.goal_lock_creation_duration_30_days),
                    selected = totalDays == 30,
                    onClick = onSelectThirtyDays,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SetupSectionCaption(text = stringResource(id = R.string.goal_lock_creation_custom_days_label))
            Spacer(modifier = Modifier.height(8.dp))
            SetupStepper(
                valueLabel = stringResource(id = R.string.goal_lock_creation_custom_days_value, totalDays),
                onDecrement = { onCustomDaysChange((totalDays - 1).coerceAtLeast(1)) },
                onIncrement = { onCustomDaysChange(totalDays + 1) },
                decrementEnabled = totalDays > 1,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupSecondaryButton(
                text = stringResource(id = R.string.goal_lock_creation_pick_end_date),
                onClick = { showEndDatePicker = true },
            )
        }

        // Lock mode
        SetupGroupCard {
            SetupSectionHeader(title = stringResource(id = R.string.goal_lock_creation_lock_mode_label))
            Spacer(modifier = Modifier.height(12.dp))
            SetupSelectableCard(
                title = stringResource(id = R.string.goal_lock_creation_lock_mode_all_day),
                subtitle = stringResource(id = R.string.goal_lock_creation_lock_mode_all_day_desc),
                selected = isAllDaySelected,
                onClick = onSetAllDay,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupSelectableCard(
                title = stringResource(id = R.string.goal_lock_creation_lock_mode_weekday_evening),
                subtitle = stringResource(id = R.string.goal_lock_creation_lock_mode_weekday_evening_desc),
                selected = isScheduledSelected,
                onClick = onSetWeekdayEvening,
            )
            if (state.hasInvalidScheduledTime) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.goal_lock_creation_invalid_scheduled_time),
                    color = KeepTheme.colors.error,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        // Apps
        SetupGroupCard {
            SetupSectionHeader(
                title = stringResource(id = R.string.goal_lock_creation_apps_label),
                valueLabel = state.selectedApps.size.toString(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            SetupSectionCaption(text = selectedAppsText)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupSecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.goal_lock_creation_reload_current_selection),
                    onClick = onReloadCurrentSelection,
                )
                SetupSecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.goal_lock_creation_open_app_picker),
                    onClick = onSelectApps,
                )
            }
            if (selectedAppItems.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SetupSectionCaption(text = stringResource(id = R.string.goal_lock_creation_apps_empty))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedAppItems.forEach { appItem ->
                        SetupAppRow(
                            packageName = appItem.packageName,
                            fallbackLabel = appItem.label,
                            removeLabel = stringResource(id = R.string.goal_lock_creation_remove_app),
                            onRemove = { onRemoveSelectedApp(appItem.packageName) },
                        )
                    }
                }
            }
        }

        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(id = R.string.goal_lock_creation_submit),
            enabled = state.isCreateEnabled,
            onClick = onCreate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(picked)
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text(
                    text = stringResource(id = android.R.string.ok),
                    color = KeepTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = android.R.string.cancel),
                    color = KeepTheme.colors.onTertiaryContainer,
                )
            }
        },
        colors = DatePickerDefaults.colors(containerColor = KeepTheme.colors.onSecondary),
    ) {
        DatePicker(state = datePickerState)
    }
}

data class GoalLockSelectedAppUiItem(
    val packageName: String,
    val label: String,
    val description: String,
)

fun buildGoalLockSelectedAppItems(
    selectedPackages: Set<String>,
    resolveLabel: (String) -> String?,
    unknownAppDescription: String,
): List<GoalLockSelectedAppUiItem> =
    selectedPackages
        .map { packageName ->
            val normalizedPackageName = packageName.trim()
            val label = resolveLabel(normalizedPackageName)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: normalizedPackageName
            GoalLockSelectedAppUiItem(
                packageName = normalizedPackageName,
                label = label,
                description = if (label == normalizedPackageName) {
                    unknownAppDescription
                } else {
                    normalizedPackageName
                },
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<GoalLockSelectedAppUiItem> { it.description == unknownAppDescription }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName },
        )

fun buildGoalLockCreationAccessibilityDescription(
    goalName: String,
    durationRangeText: String,
    lockModeText: String,
    selectedAppsText: String,
): String = listOf(
    goalName.trim(),
    durationRangeText.trim(),
    lockModeText.trim(),
    selectedAppsText.trim(),
)
    .filter { it.isNotBlank() }
    .joinToString(", ")
