package com.uiery.keep.feature.routine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val coroutineScope = rememberCoroutineScope()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is RoutineSideEffect.MoveToLock -> onNavigateLock(
                sideEffect.lockTime,
                sideEffect.isRoutine
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.analyticsRoutineScreen()
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
                addRoutine = viewModel::addRoutine,
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
                updateRoutine = viewModel::updateRoutine,
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