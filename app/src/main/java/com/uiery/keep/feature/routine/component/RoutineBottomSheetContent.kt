package com.uiery.keep.feature.routine.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.keep.Picker
import com.uiery.keep.rememberPickerState
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.util.toTimeString
import kotlinx.datetime.toJavaLocalTime
import com.uiery.keep.feature.home.component.CategoryBottomSheetContent
import com.uiery.keep.feature.routine.RoutineBottomSheetSideEffect
import com.uiery.keep.feature.routine.RoutineBottomSheetViewModel
import com.uiery.keep.model.RoutineModel
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
    onRequireAlarmPermission: () -> Unit = { },
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

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            RoutineBottomSheetSideEffect.ShowAlarmPermission -> onRequireAlarmPermission()
        }
    }

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
                changeLockHours = state.changeLockHours,
                onAppSelect = moveAppSelect,
                setName = viewModel::setName,
                setStartTime = viewModel::setStartTime,
                setEndTime = viewModel::setEndTime,
                onSelectDay = viewModel::setSelectDays,
                onChangeLockHoursChanged = viewModel::setChangeLockHours,
                onAddRoutine = {
                    onCloseBottomSheet()
                    viewModel.addRoutine()
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
    changeLockHours: Int?,
    onAppSelect: () -> Unit,
    setName: (String) -> Unit,
    setStartTime: (LocalTime) -> Unit,
    setEndTime: (LocalTime) -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onChangeLockHoursChanged: (Int?) -> Unit,
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
            RoutineChangeLockContent(
                startTime = startTime,
                changeLockHours = changeLockHours,
                onChangeLockHoursChanged = onChangeLockHoursChanged,
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
private fun RoutineChangeLockContent(
    modifier: Modifier = Modifier,
    startTime: LocalTime,
    changeLockHours: Int?,
    onChangeLockHoursChanged: (Int?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val offText = stringResource(R.string.change_lock_off)
    val selectedText = if (changeLockHours == null) {
        offText
    } else {
        stringResource(R.string.change_lock_hours, changeLockHours)
    }

    // items: "사용 안 함", "1", "2", ... "24"
    val pickerItems = remember { listOf(offText) + (1..24).map { it.toString() } }
    val pickerState = rememberPickerState()

    if (showDialog) {
        Dialog(onDismissRequest = {
            val selected = pickerState.selectedItem
            if (selected == offText || selected.isEmpty()) {
                onChangeLockHoursChanged(null)
            } else {
                onChangeLockHoursChanged(selected.toIntOrNull())
            }
            showDialog = false
        }) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .background(
                        color = KeepTheme.colors.onSecondary,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(32.dp)
                        .background(
                            shape = RoundedCornerShape(8.dp),
                            color = KeepTheme.colors.tertiary,
                        )
                )
                Picker(
                    state = pickerState,
                    items = pickerItems,
                    startIndex = if (changeLockHours == null) 0 else changeLockHours,
                    visibleItemsCount = 5,
                    isInfinity = true,
                    color = KeepTheme.colors.onSurfaceVariant,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    ),
                    textModifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = KeepTheme.colors.secondary,
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.change_lock_title),
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            RoutineTimeButton(
                text = selectedText,
                onClick = { showDialog = true },
            )
        }
        if (changeLockHours != null) {
            val context = LocalContext.current
            val lockStartTimeText = remember(startTime, changeLockHours) {
                val lockStart = kotlinx.datetime.LocalTime(startTime.hour, startTime.minute)
                    .toJavaLocalTime()
                    .minusHours(changeLockHours.toLong())
                kotlinx.datetime.LocalTime(lockStart.hour, lockStart.minute)
                    .toTimeString(context)
            }
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                text = stringResource(R.string.change_lock_description, lockStartTimeText),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 12.sp,
            )
        }
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
                    contentDescription = stringResource(R.string.cd_navigate_back),
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
