package com.uiery.keep.feature.goallock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalLockDetailScreen(
    viewModel: GoalLockDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val appSelectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isAppSelectionSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadGoalLock()
    }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            GoalLockDetailSideEffect.NotFound,
            GoalLockDetailSideEffect.Ended,
            -> onNavigateBack()
        }
    }

    if (isAppSelectionSheetVisible) {
        ModalBottomSheet(
            sheetState = appSelectionSheetState,
            onDismissRequest = { isAppSelectionSheetVisible = false },
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = uiState.goalLock?.selectedPackages.orEmpty(),
                onComplete = { selectedApps ->
                    viewModel.requestUpdateSelectedApps(selectedApps)
                    isAppSelectionSheetVisible = false
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.goal_lock_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                ),
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        GoalLockDetailContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            state = uiState,
            onRequestEnd = viewModel::requestEndGoalLock,
            onCancelEnd = viewModel::cancelEndGoalLock,
            onConfirmEnd = { viewModel.confirmEndGoalLock() },
            onGoalNameChange = viewModel::requestUpdateGoalName,
            onCancelUpdateGoalName = viewModel::cancelUpdateGoalName,
            onConfirmUpdateGoalName = viewModel::confirmUpdateGoalName,
            onRequestUpdateApps = { isAppSelectionSheetVisible = true },
            onCancelUpdateApps = viewModel::cancelUpdateSelectedApps,
            onConfirmUpdateApps = viewModel::confirmUpdateSelectedApps,
            onDurationDaysChange = viewModel::requestUpdateDurationDays,
            onCancelUpdateDuration = viewModel::cancelUpdateDuration,
            onConfirmUpdateDuration = viewModel::confirmUpdateDuration,
            onLockModeChange = viewModel::requestUpdateLockMode,
            onCancelUpdateLockMode = viewModel::cancelUpdateLockMode,
            onConfirmUpdateLockMode = viewModel::confirmUpdateLockMode,
        )
    }
}

@Composable
internal fun GoalLockDetailContent(
    modifier: Modifier = Modifier,
    state: GoalLockDetailUiState,
    onRequestEnd: () -> Unit,
    onCancelEnd: () -> Unit,
    onConfirmEnd: () -> Unit,
    onGoalNameChange: (String) -> Unit,
    onCancelUpdateGoalName: () -> Unit,
    onConfirmUpdateGoalName: () -> Unit,
    onRequestUpdateApps: (Set<String>) -> Unit,
    onCancelUpdateApps: () -> Unit,
    onConfirmUpdateApps: () -> Unit,
    onDurationDaysChange: (Int) -> Unit,
    onCancelUpdateDuration: () -> Unit,
    onConfirmUpdateDuration: () -> Unit,
    onLockModeChange: (GoalLockMode) -> Unit,
    onCancelUpdateLockMode: () -> Unit,
    onConfirmUpdateLockMode: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.goalLock == null) {
            Text(
                text = stringResource(id = R.string.goal_lock_detail_loading),
                color = KeepTheme.colors.onSurfaceVariant,
            )
            return@Column
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
                    text = state.goalName,
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    text = stringResource(
                        id = R.string.goal_lock_detail_summary,
                        goalLockModeDetailLabel(lockMode = state.goalLock.lockMode),
                        state.selectedAppCount,
                    ),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(
                        id = when {
                            state.isCompleted -> R.string.goal_lock_detail_status_completed
                            state.isEnded -> R.string.goal_lock_detail_status_ended
                            else -> R.string.goal_lock_detail_status_active
                        },
                    ),
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        if (!state.isEnded && !state.isCompleted) {
            if (state.showUpdateAppsConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.goal_lock_detail_update_apps_confirmation,
                                state.pendingSelectedApps.size,
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onCancelUpdateApps) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_apps_cancel))
                            }
                            Button(onClick = onConfirmUpdateApps) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_apps_save))
                            }
                        }
                    }
                }
            }

            if (state.showUpdateGoalNameConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.goal_lock_detail_update_name_confirmation,
                                state.pendingGoalName.trim(),
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onCancelUpdateGoalName) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_name_cancel))
                            }
                            Button(onClick = onConfirmUpdateGoalName) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_name_save))
                            }
                        }
                    }
                }
            }

            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.pendingGoalName,
                onValueChange = onGoalNameChange,
                label = { Text(text = stringResource(id = R.string.goal_lock_detail_goal_name_label)) },
                singleLine = true,
            )

            Text(
                text = stringResource(id = R.string.goal_lock_detail_duration_label),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDurationDaysChange(7) }) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_duration_option_7_days))
                }
                OutlinedButton(onClick = { onDurationDaysChange(14) }) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_duration_option_14_days))
                }
                OutlinedButton(onClick = { onDurationDaysChange(30) }) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_duration_option_30_days))
                }
            }

            if (state.showUpdateDurationConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.goal_lock_detail_update_duration_confirmation,
                                state.pendingDurationDays,
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onCancelUpdateDuration) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_duration_cancel))
                            }
                            Button(onClick = onConfirmUpdateDuration) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_duration_save))
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(id = R.string.goal_lock_detail_lock_mode_label),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLockModeChange(GoalLockMode.AllDay) }) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_lock_mode_all_day))
                }
                OutlinedButton(onClick = { onLockModeChange(weekdayEveningGoalLockMode()) }) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_lock_mode_weekday_evening))
                }
            }

            if (state.showUpdateLockModeConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.goal_lock_detail_update_lock_mode_confirmation,
                                goalLockModeDetailLabel(lockMode = state.pendingLockMode),
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onCancelUpdateLockMode) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_lock_mode_cancel))
                            }
                            Button(onClick = onConfirmUpdateLockMode) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_update_lock_mode_save))
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = { onRequestUpdateApps(state.goalLock.selectedPackages) }) {
                Text(text = stringResource(id = R.string.goal_lock_detail_update_apps_cta))
            }

            if (state.showEndConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.goal_lock_detail_end_confirmation),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onCancelEnd) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_end_cancel))
                            }
                            Button(onClick = onConfirmEnd) {
                                Text(text = stringResource(id = R.string.goal_lock_detail_end_confirm))
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onRequestEnd) {
                    Text(text = stringResource(id = R.string.goal_lock_detail_end_cta))
                }
            }
        }
    }
}

private fun weekdayEveningGoalLockMode(): GoalLockMode.Scheduled = GoalLockMode.Scheduled(
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

@Composable
private fun goalLockModeDetailLabel(lockMode: GoalLockMode?): String = when (lockMode) {
    GoalLockMode.AllDay -> stringResource(id = R.string.goal_lock_detail_lock_mode_all_day)
    is GoalLockMode.Scheduled -> stringResource(id = R.string.goal_lock_detail_lock_mode_weekday_evening)
    null -> ""
}
