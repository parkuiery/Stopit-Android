package com.uiery.keep.feature.routine

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.uiery.kds.KeepSnackBar
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
    repeatBlockSuggestionSurface: String? = null,
    repeatBlockSuggestion: RepeatBlockRoutineSuggestion? = null,
    onNavigateBack: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
) {
    val state by viewModel.collectAsState()
    val routineBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val snackBarHostState = remember { SnackbarHostState() }
    val alarmPermissionBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAlarmPermissionBottomSheet by remember { mutableStateOf(false) }
    val activeRoutineBlockedMessage = stringResource(R.string.routine_active_action_blocked_message)

    // Check alarm permission on entry if routines exist (show once ever, persisted)
    LaunchedEffect(state.routines) {
        if (state.routines.isNotEmpty()) {
            viewModel.checkAlarmPermissionNeeded()
        }
    }

    LaunchedEffect(repeatBlockSuggestionSurface, repeatBlockSuggestion) {
        if (repeatBlockSuggestionSurface != null && repeatBlockSuggestion != null) {
            viewModel.showRoutineBottomSheet()
        }
    }

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is RoutineSideEffect.MoveToLock -> onNavigateLock(
                sideEffect.lockTime,
                sideEffect.isRoutine
            )
            is RoutineSideEffect.ShowAlarmPermission -> {
                showAlarmPermissionBottomSheet = true
            }
            is RoutineSideEffect.ShowActiveRoutineBlocked -> {
                coroutineScope.launch {
                    snackBarHostState.showSnackbar(activeRoutineBlockedMessage)
                }
            }
            is RoutineSideEffect.ShareRoutineTemplate -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, sideEffect.payload.text)
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.routine_template_share_chooser_title),
                )
                runCatching {
                    context.startActivity(chooser)
                }.onSuccess {
                    viewModel.routineTemplateShareSheetOpened(sideEffect.payload)
                }.onFailure { error ->
                    if (error is ActivityNotFoundException) {
                        viewModel.routineTemplateShareFailed(sideEffect.payload)
                    } else {
                        throw error
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
                                viewModel.markAlarmPermissionShown()
                                RoutineAlarmPermissionSettingsLauncher.open(
                                    exactAlarmTarget = createExactAlarmSettingsIntent(context.packageName),
                                    appDetailsTarget = createAppDetailsSettingsIntent(context.packageName),
                                    launch = context::startActivity,
                                )
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
                repeatBlockSuggestionSurface = repeatBlockSuggestionSurface,
                repeatBlockSuggestion = repeatBlockSuggestion,
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
                    showAlarmPermissionBottomSheet = true
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
                            contentDescription = stringResource(R.string.cd_delete_routine),
                            tint = KeepTheme.colors.error,
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
                    showAlarmPermissionBottomSheet = true
                },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
        snackbarHost = {
            SnackbarHost(snackBarHostState) {
                KeepSnackBar(snackbarData = it)
            }
        },
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
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showRoutineBottomSheet
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.cd_add_routine),
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
                    onShareClick = viewModel::shareRoutineTemplate,
                    onBlockedRoutineAction = {
                        coroutineScope.launch {
                            snackBarHostState.showSnackbar(activeRoutineBlockedMessage)
                        }
                    },
                )
            }
        }
    }
}
