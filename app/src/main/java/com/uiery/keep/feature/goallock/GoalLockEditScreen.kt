package com.uiery.keep.feature.goallock

import androidx.activity.compose.BackHandler
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
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.uiery.kds.KeepIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.uiery.kds.KeepTopAppBar
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepSnackbarHost
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.goallock.GoalLockMode
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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalLockEditScreen(
    viewModel: GoalLockEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAppPicker by remember { mutableStateOf(false) }
    val saveErrorMessage = stringResource(id = R.string.goal_lock_edit_save_error)

    LaunchedEffect(Unit) {
        viewModel.loadGoalLock()
    }
    LaunchedEffect(state.error) {
        if (state.error == GoalLockEditError.Save) {
            snackbarHostState.showSnackbar(saveErrorMessage)
        }
    }
    viewModel.collectSideEffect { effect ->
        when (effect) {
            GoalLockEditSideEffect.Saved -> onSaved()
            GoalLockEditSideEffect.NavigateBack,
            GoalLockEditSideEffect.NotFound,
            GoalLockEditSideEffect.Unavailable,
            -> onNavigateBack()
        }
    }

    BackHandler { viewModel.requestBack() }

    if (showAppPicker) {
        KeepModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showAppPicker = false },
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = state.selectedPackages,
                onComplete = {
                    viewModel.setSelectedApps(it)
                    showAppPicker = false
                },
            )
        }
    }

    if (state.showDiscardConfirmation) {
        KeepConfirmationDialog(
            title = stringResource(id = R.string.goal_lock_edit_discard_title),
            message = stringResource(id = R.string.goal_lock_edit_discard_body),
            confirmLabel = stringResource(id = R.string.goal_lock_edit_discard),
            dismissLabel = stringResource(id = R.string.goal_lock_edit_keep_editing),
            confirmTone = KeepConfirmationTone.Critical,
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::cancelDiscard,
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeepTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.goal_lock_edit_title),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    KeepIconButton(onClick = viewModel::requestBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { KeepSnackbarHost(hostState = snackbarHostState) },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Text(
                    modifier = Modifier.padding(paddingValues).padding(20.dp),
                    text = stringResource(id = R.string.goal_lock_detail_loading),
                    color = KeepTheme.colors.onSurfaceVariant,
                )
            }
            state.originalGoal == null -> {
                SetupGroupCard(
                    modifier = Modifier.padding(paddingValues).padding(20.dp),
                ) {
                    SetupSectionCaption(
                        text = stringResource(id = R.string.goal_lock_edit_load_error),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SetupSecondaryButton(
                        text = stringResource(id = R.string.goal_lock_edit_retry),
                        onClick = viewModel::retryLoad,
                    )
                }
            }
            else -> GoalLockEditContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                state = state,
                onGoalNameChange = viewModel::setGoalName,
                onDurationDaysChange = viewModel::setDurationDays,
                onEndDateChange = viewModel::setEndDate,
                onSetAllDay = viewModel::setAllDayMode,
                onSetWeekdayEvening = viewModel::setWeekdayEveningMode,
                onSelectApps = { showAppPicker = true },
                onRemoveApp = viewModel::removeSelectedApp,
                onSave = { viewModel.save() },
            )
        }
    }
}

@Composable
internal fun GoalLockEditContent(
    state: GoalLockEditUiState,
    onGoalNameChange: (String) -> Unit,
    onDurationDaysChange: (Int) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onSetAllDay: () -> Unit,
    onSetWeekdayEvening: () -> Unit,
    onSelectApps: () -> Unit,
    onRemoveApp: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDate = state.startDate ?: return
    val endDate = state.endDate ?: return
    val lockMode = state.lockMode ?: return
    var showDatePicker by remember { mutableStateOf(false) }
    val dateRange = stringResource(R.string.goal_lock_edit_duration_range, startDate, endDate)
    val modeText = goalLockEditModeLabel(lockMode)
    val appsText = stringResource(R.string.goal_lock_edit_apps_summary, state.selectedPackages.size)
    val summary = buildGoalLockEditAccessibilityDescription(
        goalName = state.goalName,
        durationRangeText = dateRange,
        lockModeText = modeText,
        selectedAppsText = appsText,
    )

    if (showDatePicker) {
        GoalLockEndDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                onEndDateChange(it)
            },
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        SetupHero(
            modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
            iconResId = R.drawable.ic_goal_lock,
            title = stringResource(id = R.string.goal_lock_edit_title),
            subtitle = stringResource(id = R.string.goal_lock_edit_hero_subtitle),
        )

        SetupGroupCard {
            SetupSectionHeader(title = stringResource(id = R.string.goal_lock_detail_goal_name_label))
            Spacer(modifier = Modifier.height(12.dp))
            SetupTextField(
                value = state.goalName,
                onValueChange = onGoalNameChange,
                placeholder = stringResource(id = R.string.goal_lock_creation_goal_name_placeholder),
                isError = state.goalName.isBlank(),
            )
        }

        SetupGroupCard {
            SetupSectionHeader(
                title = stringResource(id = R.string.goal_lock_detail_duration_label),
                valueLabel = dateRange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30).forEach { days ->
                    SetupChip(
                        label = stringResource(id = R.string.goal_lock_edit_duration_days, days),
                        selected = state.totalDurationDays == days,
                        onClick = { onDurationDaysChange(days) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SetupStepper(
                valueLabel = stringResource(id = R.string.goal_lock_edit_duration_days, state.totalDurationDays),
                onDecrement = { onDurationDaysChange((state.totalDurationDays - 1).coerceAtLeast(1)) },
                onIncrement = { onDurationDaysChange(state.totalDurationDays + 1) },
                decrementEnabled = state.totalDurationDays > 1,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupSecondaryButton(
                text = stringResource(id = R.string.goal_lock_creation_pick_end_date),
                onClick = { showDatePicker = true },
            )
        }

        SetupGroupCard {
            SetupSectionHeader(title = stringResource(id = R.string.goal_lock_detail_lock_mode_label))
            Spacer(modifier = Modifier.height(12.dp))
            SetupSelectableCard(
                title = stringResource(id = R.string.goal_lock_creation_lock_mode_all_day),
                subtitle = stringResource(id = R.string.goal_lock_creation_lock_mode_all_day_desc),
                selected = lockMode == GoalLockMode.AllDay,
                onClick = onSetAllDay,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupSelectableCard(
                title = stringResource(id = R.string.goal_lock_creation_lock_mode_weekday_evening),
                subtitle = if (lockMode is GoalLockMode.Scheduled) {
                    goalLockEditModeLabel(lockMode)
                } else {
                    stringResource(id = R.string.goal_lock_creation_lock_mode_weekday_evening_desc)
                },
                selected = lockMode is GoalLockMode.Scheduled,
                onClick = onSetWeekdayEvening,
            )
        }

        SetupGroupCard {
            SetupSectionHeader(
                title = stringResource(id = R.string.goal_lock_creation_apps_label),
                valueLabel = state.selectedPackages.size.toString(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            SetupSectionCaption(text = appsText)
            Spacer(modifier = Modifier.height(12.dp))
            SetupSecondaryButton(
                text = stringResource(id = R.string.goal_lock_edit_select_apps),
                onClick = onSelectApps,
            )
            if (state.selectedPackages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectedPackages.sorted().forEach { packageName ->
                        SetupAppRow(
                            packageName = packageName,
                            fallbackLabel = packageName,
                            removeLabel = stringResource(id = R.string.goal_lock_creation_remove_app),
                            onRemove = { onRemoveApp(packageName) },
                        )
                    }
                }
            }
        }

        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(
                id = if (state.isSaving) R.string.goal_lock_edit_saving else R.string.goal_lock_edit_save,
            ),
            enabled = state.canSave,
            onClick = onSave,
        )
    }
}

@Composable
private fun goalLockEditModeLabel(lockMode: GoalLockMode): String = when (lockMode) {
    GoalLockMode.AllDay -> stringResource(id = R.string.goal_lock_detail_lock_mode_all_day)
    is GoalLockMode.Scheduled -> stringResource(
        id = R.string.goal_lock_edit_schedule_summary,
        lockMode.repeatDays
            .sortedBy { it.value }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
        lockMode.startTime,
        lockMode.endTime,
    )
}
