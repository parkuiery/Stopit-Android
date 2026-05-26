package com.uiery.keep.feature.routine

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.routine.component.RoutineBottomSheetContent
import com.uiery.keep.feature.routine.component.RoutineListContent
import com.uiery.keep.feature.routine.component.RoutineNoContent
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    modifier: Modifier = Modifier,
    viewModel: RoutineViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
) {
    val state by viewModel.collectAsState()
    val routineBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val alarmPermissionBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAlarmPermissionBottomSheet by remember { mutableStateOf(false) }

    fun checkAndShowAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                showAlarmPermissionBottomSheet = true
            }
        }
    }

    // Check alarm permission on entry if routines exist (show once ever, persisted)
    LaunchedEffect(state.routines) {
        if (state.routines.isNotEmpty()) {
            viewModel.checkAlarmPermissionNeeded()
        }
    }

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is RoutineSideEffect.MoveToLock -> onNavigateLock(
                sideEffect.lockTime,
                sideEffect.isRoutine
            )
            is RoutineSideEffect.ShowAlarmPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        showAlarmPermissionBottomSheet = true
                        viewModel.markAlarmPermissionShown()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.analyticsRoutineScreen()
    }

    // Alarm permission bottom sheet
    if (showAlarmPermissionBottomSheet) {
        KeepModalBottomSheet(
            sheetState = alarmPermissionBottomSheetState,
            onDismissRequest = { showAlarmPermissionBottomSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.routine_alarm_permission_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.routine_alarm_permission_description),
                    color = KeepTheme.colors.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(18.dp))
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.routine_alarm_permission_button),
                    onClick = {
                        coroutineScope.launch {
                            alarmPermissionBottomSheetState.hide()
                        }.invokeOnCompletion {
                            if (!alarmPermissionBottomSheetState.isVisible) {
                                showAlarmPermissionBottomSheet = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    if (state.isShowRoutineBottomSheet) {
        KeepModalBottomSheet(
            sheetState = routineBottomSheetState,
            onDismissRequest = viewModel::hideRoutineBottomSheet,
        ) {
            RoutineBottomSheetContent(
                isEdit = false,
                onCloseBottomSheet = {
                    coroutineScope.launch {
                        routineBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!routineBottomSheetState.isVisible) {
                            viewModel.hideRoutineBottomSheet()
                        }
                    }
                },
                onRequireAlarmPermission = {
                    checkAndShowAlarmPermission()
                    viewModel.markAlarmPermissionShown()
                },
            )
        }
    }

    if (state.isShowEditRoutineBottomSheet && state.selectedRoutine != null) {
        KeepModalBottomSheet(
            sheetState = routineBottomSheetState,
            onDismissRequest = viewModel::hideEditRoutineBottomSheet,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier.align(Alignment.Center),
                        color = KeepTheme.colors.tertiaryContainer
                    )
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.CenterEnd),
                        onClick = {
                            viewModel.deleteRoutine(state.selectedRoutine!!.id)
                            coroutineScope.launch {
                                routineBottomSheetState.hide()
                            }.invokeOnCompletion {
                                if (!routineBottomSheetState.isVisible) {
                                    viewModel.hideEditRoutineBottomSheet()
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
                        )
                    }
                }
            }
        ) {
            RoutineBottomSheetContent(
                isEdit = true,
                routine = state.selectedRoutine,
                onCloseBottomSheet = {
                    coroutineScope.launch {
                        routineBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!routineBottomSheetState.isVisible) {
                            viewModel.hideEditRoutineBottomSheet()
                        }
                    }
                },
                onRequireAlarmPermission = {
                    checkAndShowAlarmPermission()
                    viewModel.markAlarmPermissionShown()
                },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.my_routine),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showRoutineBottomSheet
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.routines.isEmpty()) {
                RoutineNoContent(
                    modifier = Modifier.fillMaxSize(),
                    onAddRoutine = viewModel::showRoutineBottomSheet,
                )
            } else {
                RoutineListContent(
                    routines = state.routines,
                    onEnabledChange = viewModel::changeEnabled,
                    onDetailClick = viewModel::getRoutineDetail,
                )
            }
        }
    }
}
