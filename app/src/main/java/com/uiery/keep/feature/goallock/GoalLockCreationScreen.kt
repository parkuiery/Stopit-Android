package com.uiery.keep.feature.goallock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.util.rememberAppDisplayMetadataResolver
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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
        ModalBottomSheet(
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
                title = { Text(text = title) },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
    var customDaysText by remember { mutableStateOf("") }
    var endDateText by remember { mutableStateOf("") }
    val appDisplayMetadataResolver = rememberAppDisplayMetadataResolver()
    val unknownAppDescription = stringResource(id = R.string.goal_lock_creation_app_label_missing)
    val selectedAppItems = buildGoalLockSelectedAppItems(
        selectedPackages = state.selectedApps,
        resolveLabel = { packageName -> appDisplayMetadataResolver.resolve(packageName).label },
        unknownAppDescription = unknownAppDescription,
    )

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.goal_lock_creation_goal_name_label),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.goalName,
                    onValueChange = onGoalNameChange,
                    placeholder = { Text(stringResource(id = R.string.goal_lock_creation_goal_name_placeholder)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPresetExam) { Text(stringResource(id = R.string.goal_lock_creation_preset_exam)) }
                    TextButton(onClick = onPresetSns) { Text(stringResource(id = R.string.goal_lock_creation_preset_sns)) }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.goal_lock_creation_duration_label),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = stringResource(
                        id = R.string.goal_lock_creation_duration_range,
                        state.startDate,
                        state.endDate,
                    ),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSelectSevenDays) { Text(stringResource(id = R.string.goal_lock_creation_duration_7_days)) }
                    OutlinedButton(onClick = onSelectFourteenDays) { Text(stringResource(id = R.string.goal_lock_creation_duration_14_days)) }
                    OutlinedButton(onClick = onSelectThirtyDays) { Text(stringResource(id = R.string.goal_lock_creation_duration_30_days)) }
                }
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = customDaysText,
                    onValueChange = { value ->
                        customDaysText = value.filter { it.isDigit() }.take(3)
                        customDaysText.toIntOrNull()?.let(onCustomDaysChange)
                    },
                    placeholder = { Text(stringResource(id = R.string.goal_lock_creation_custom_days_placeholder)) },
                    singleLine = true,
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = endDateText,
                    onValueChange = { value ->
                        endDateText = value.take(10)
                        runCatching { LocalDate.parse(endDateText) }.getOrNull()?.let(onEndDateChange)
                    },
                    placeholder = { Text(stringResource(id = R.string.goal_lock_creation_end_date_placeholder)) },
                    singleLine = true,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.goal_lock_creation_lock_mode_label),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSetAllDay) {
                        Text(stringResource(id = R.string.goal_lock_creation_lock_mode_all_day))
                    }
                    OutlinedButton(onClick = onSetWeekdayEvening) {
                        Text(stringResource(id = R.string.goal_lock_creation_lock_mode_weekday_evening))
                    }
                }
                Text(
                    text = when (val mode = state.lockMode) {
                        GoalLockCreationLockMode.AllDay -> stringResource(id = R.string.goal_lock_creation_current_mode_all_day)
                        is GoalLockCreationLockMode.Scheduled -> stringResource(
                            id = R.string.goal_lock_creation_current_mode_scheduled,
                            mode.repeatDays.size,
                            mode.startTime,
                            mode.endTime,
                        )
                    },
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                if (state.hasInvalidScheduledTime) {
                    Text(
                        text = stringResource(id = R.string.goal_lock_creation_invalid_scheduled_time),
                        color = KeepTheme.colors.error,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(
                        id = R.string.goal_lock_creation_selected_apps_helper,
                        state.selectedApps.size,
                    ),
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = onReloadCurrentSelection) {
                    Text(stringResource(id = R.string.goal_lock_creation_reload_current_selection))
                }
                OutlinedButton(onClick = onSelectApps) {
                    Text(stringResource(id = R.string.goal_lock_creation_open_app_picker))
                }
                selectedAppItems.forEach { appItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appItem.label,
                                color = KeepTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = appItem.description,
                                color = KeepTheme.colors.surfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                        TextButton(onClick = { onRemoveSelectedApp(appItem.packageName) }) {
                            Text(stringResource(id = R.string.goal_lock_creation_remove_app))
                        }
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isCreateEnabled,
            onClick = onCreate,
        ) {
            Text(stringResource(id = R.string.goal_lock_creation_submit))
        }
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
