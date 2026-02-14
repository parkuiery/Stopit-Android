package com.uiery.keep.feature.routine.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.CategoryBottomSheetContent
import com.uiery.keep.feature.routine.RoutineBottomSheetSideEffect
import com.uiery.keep.feature.routine.RoutineBottomSheetViewModel
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.network.routine.GetDetailRoutineResponse
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.DayOfWeek

@Composable
fun RoutineBottomSheetContent(
    modifier: Modifier = Modifier,
    viewModel: RoutineBottomSheetViewModel = hiltViewModel(),
    isEdit: Boolean,
    routine: RoutineModel? = null,
    addRoutine: () -> Unit = { },
    //updateRoutine: (RoutineModel) -> Unit = { },
    onCloseBottomSheet: () -> Unit,
) {
    val state by viewModel.collectAsState()
    val pagerState = rememberPagerState(pageCount = {
        2
    })
    val coroutineScope = rememberCoroutineScope()
    val moveAppSelect: () -> Unit = {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = 1,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }
    val moveRoutineSetting: () -> Unit = {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = 0,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(Unit) {
        if (isEdit) {
            routine?.let {
                viewModel.resetEditState(it)
            }
        } else {
            viewModel.resetState()
        }
    }

//    viewModel.collectSideEffect { effect ->
//        when (effect) {
//            is RoutineBottomSheetSideEffect.AddRoutineSuccess -> addRoutine(effect.getDetailRoutineResponse)
//            is RoutineBottomSheetSideEffect.UpdateRoutineSuccess -> updateRoutine(effect.getDetailRoutineResponse)
//        }
//    }

    HorizontalPager(
        modifier = modifier.fillMaxSize(),
        state = pagerState,
        userScrollEnabled = false,
    ) { page ->
        when (page) {
            0 -> RoutineInputContent(
                isEdit = isEdit,
                name = state.name,
                startTime = state.startTime,
                endTime = state.endTime,
                selectDays = state.selectDays,
                isButtonEnabled = state.isButtonEnable,
                selectApps = state.selectApps,
                onAppSelect = moveAppSelect,
                setName = viewModel::setName,
                setStartTime = viewModel::setStartTime,
                setEndTime = viewModel::setEndTime,
                onSelectDay = viewModel::setSelectDays,
                onAddRoutine = {
                    onCloseBottomSheet()
                    viewModel.addRoutine()
                    addRoutine()
                },
                onEditRoutine = {
                    onCloseBottomSheet()
                    viewModel.editRoutine(routine?.id)
                }
            )

            1 -> {
                RoutineAppSelectionContent(
                    onBackClick = moveRoutineSetting,
                    selectApps = state.selectApps,
                    setSelectApps = viewModel::setSelectApps,
                )
            }
        }
    }
}

@Composable
private fun RoutineInputContent(
    modifier: Modifier = Modifier,
    isEdit: Boolean,
    name: String,
    startTime: LocalTime,
    endTime: LocalTime,
    selectDays: List<DayOfWeek>,
    isButtonEnabled: Boolean,
    selectApps: Set<String>,
    onAppSelect: () -> Unit,
    setName: (String) -> Unit,
    setStartTime: (LocalTime) -> Unit,
    setEndTime: (LocalTime) -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        val buttonText = if (isEdit) R.string.routine_edit_button else R.string.routine_add_button
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AppSelectButton(
                modifier = Modifier.padding(top = 20.dp),
                selectApps = selectApps,
                onClick = onAppSelect,
            )
            RoutineNameContent(
                name = name,
                setName = setName,
            )
            RoutineTimeContent(
                startTime = startTime,
                endTime = endTime,
                setStartTime = setStartTime,
                setEndTime = setEndTime,
            )
            RoutineDayContent(
                selectDays = selectDays,
                onSelectDay = onSelectDay
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(buttonText),
            enabled = isButtonEnabled,
            onClick = {
                if (isEdit) {
                    onEditRoutine()
                } else {
                    onAddRoutine()
                }
            },
        )
    }
}

@Composable
private fun RoutineAppSelectionContent(
    modifier: Modifier = Modifier,
    selectApps: Set<String>,
    setSelectApps: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row {
            IconButton(
                onClick = onBackClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                    contentDescription = null,
                    tint = KeepTheme.colors.primary,
                )
            }
        }
        CategoryBottomSheetContent(
            storeSelectApps = selectApps,
            onComplete = {
                setSelectApps(it)
                onBackClick()
            },
        )
    }
}