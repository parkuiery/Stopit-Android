package com.uiery.keep.feature.routine.component

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepChip
import com.uiery.kds.KeepChipRole
import com.uiery.kds.KeepChipSize
import com.uiery.kds.KeepChipVariant
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.kds.KeepDialog
import com.uiery.kds.KeepDivider
import com.uiery.kds.KeepField
import com.uiery.kds.KeepFieldHelperTone
import com.uiery.kds.KeepFieldRequirement
import com.uiery.kds.KeepIconButton
import com.uiery.kds.KeepInputButton
import com.uiery.kds.KeepLabel
import com.uiery.kds.KeepLabelSize
import com.uiery.kds.KeepLabelTone
import com.uiery.kds.KeepLabelWeight
import com.uiery.kds.KeepMenu
import com.uiery.kds.KeepMenuItem
import com.uiery.kds.KeepMenuItemTone
import com.uiery.kds.KeepTextButton
import com.uiery.kds.KeepTextField
import com.uiery.kds.KeepTextFieldVariant
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.BuildConfig
import com.uiery.keep.Picker
import com.uiery.keep.R
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.usageinsight.UsageInsightRoutinePrefill
import com.uiery.keep.feature.routine.RoutineBottomSheetSideEffect
import com.uiery.keep.feature.routine.RoutineBottomSheetViewModel
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.rememberPickerState
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.TimerPicker
import com.uiery.keep.ui.component.WebsiteBlockingWarningRow
import com.uiery.keep.util.formatWeekdayShort
import com.uiery.keep.util.routineDurationMinutes
import com.uiery.keep.util.toTimeString
import com.uiery.keep.websiteblocking.WebsiteBlockingRuntimeState
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus
import java.time.DayOfWeek
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun RoutineBottomSheetContent(
    modifier: Modifier = Modifier,
    viewModel: RoutineBottomSheetViewModel = hiltViewModel(),
    isEdit: Boolean,
    routine: RoutineModel? = null,
    repeatBlockSuggestionSurface: String? = null,
    repeatBlockSuggestion: RepeatBlockRoutineSuggestion? = null,
    usageInsightRoutinePrefill: UsageInsightRoutinePrefill? = null,
    routineSavedEntrySurface: String? = null,
    routineSavedCreationSource: String? = null,
    onRequireAlarmPermission: () -> Unit = { },
    onActiveRoutineBlocked: () -> Unit = { },
    onDeleteRoutine: (() -> Unit)? = null,
    onCloseBottomSheet: () -> Unit,
) {
    val state by viewModel.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val moveAppSelect: () -> Unit = {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = 1,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    val moveRoutineSetting: () -> Unit = {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = 0,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    LaunchedEffect(
        isEdit,
        routine,
        repeatBlockSuggestionSurface,
        repeatBlockSuggestion,
        usageInsightRoutinePrefill,
        routineSavedEntrySurface,
        routineSavedCreationSource,
    ) {
        if (isEdit) {
            routine?.let(viewModel::resetEditState)
        } else if (repeatBlockSuggestionSurface != null && repeatBlockSuggestion != null) {
            viewModel.applyRepeatBlockRoutineSuggestionPrefill(
                surface = repeatBlockSuggestionSurface,
                suggestion = repeatBlockSuggestion,
            )
        } else if (usageInsightRoutinePrefill != null) {
            viewModel.applyUsageInsightRoutinePrefill(
                prefill = usageInsightRoutinePrefill,
                routineSavedCreationSource = routineSavedCreationSource,
            )
        } else {
            viewModel.resetState(
                routineSavedEntrySurface = routineSavedEntrySurface,
                routineSavedCreationSource = routineSavedCreationSource,
            )
        }
    }

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            RoutineBottomSheetSideEffect.ShowAlarmPermission -> onRequireAlarmPermission()
            RoutineBottomSheetSideEffect.ShowActiveRoutineBlocked -> onActiveRoutineBlocked()
            RoutineBottomSheetSideEffect.CloseBottomSheet -> onCloseBottomSheet()
        }
    }

    HorizontalPager(
        modifier = modifier.fillMaxHeight(0.9f),
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
                selectWebDomains = state.selectWebDomains,
                changeLockHours = state.changeLockHours,
                onAppSelect = moveAppSelect,
                setName = viewModel::setName,
                setStartTime = viewModel::setStartTime,
                setEndTime = viewModel::setEndTime,
                onSelectDay = viewModel::setSelectDays,
                onChangeLockHoursChanged = viewModel::setChangeLockHours,
                onDeleteRoutine = onDeleteRoutine,
                onCloseBottomSheet = onCloseBottomSheet,
                onAddRoutine = viewModel::addRoutine,
                onEditRoutine = { viewModel.editRoutine(routine?.id) },
            )

            1 -> RoutineAppSelectionContent(
                onBackClick = moveRoutineSetting,
                selectApps = state.selectApps,
                selectWebDomains = state.selectWebDomains,
                setSelectApps = viewModel::setSelectApps,
                setSelectTargets = viewModel::setSelectTargets,
            )
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
    selectWebDomains: Set<String>,
    changeLockHours: Int?,
    onAppSelect: () -> Unit,
    setName: (String) -> Unit,
    setStartTime: (LocalTime) -> Unit,
    setEndTime: (LocalTime) -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onChangeLockHoursChanged: (Int?) -> Unit,
    onDeleteRoutine: (() -> Unit)?,
    onCloseBottomSheet: () -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: () -> Unit,
) {
    val buttonText = stringResource(
        if (isEdit) R.string.routine_edit_button else R.string.routine_add_button,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        RoutineSheetHeader(
            isEdit = isEdit,
            routineName = name,
            onDeleteRoutine = onDeleteRoutine,
            onCloseBottomSheet = onCloseBottomSheet,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RoutineAppField(
                selectApps = selectApps,
                selectWebDomains = selectWebDomains,
                onClick = onAppSelect,
            )
            RoutineWebsiteConsentWarning(hasWebsiteTargets = selectWebDomains.isNotEmpty())
            KeepTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = setName,
                fieldLabel = stringResource(R.string.routine_name_placeholder),
                requirement = KeepFieldRequirement.Required,
                singleLine = true,
                variant = KeepTextFieldVariant.Outline,
            )
            RoutineTimeField(
                startTime = startTime,
                endTime = endTime,
                setStartTime = setStartTime,
                setEndTime = setEndTime,
            )
            RoutineDayField(
                selectDays = selectDays,
                onSelectDay = onSelectDay,
            )
            RoutineProtectionField(
                startTime = startTime,
                changeLockHours = changeLockHours,
                onChangeLockHoursChanged = onChangeLockHoursChanged,
            )
        }
        RoutineSheetFooter(
            text = buttonText,
            enabled = isButtonEnabled,
            onClick = if (isEdit) onEditRoutine else onAddRoutine,
        )
    }
}

@Composable
private fun RoutineSheetHeader(
    isEdit: Boolean,
    routineName: String,
    onDeleteRoutine: (() -> Unit)?,
    onCloseBottomSheet: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    if (isEdit) {
                        R.string.routine_sheet_edit_title
                    } else {
                        R.string.routine_sheet_add_title
                    },
                ),
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.routine_sheet_description),
                color = KeepTheme.semanticColors.foreground.muted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isEdit && onDeleteRoutine != null) {
            RoutineOverflowMenu(
                routineName = routineName,
                onDeleteRoutine = onDeleteRoutine,
            )
        }
        KeepIconButton(onClick = onCloseBottomSheet) {
            Icon(
                painter = painterResource(R.drawable.outline_close_24),
                contentDescription = stringResource(R.string.cd_close_routine_sheet),
                tint = KeepTheme.semanticColors.foreground.neutral,
            )
        }
    }
}

@Composable
private fun RoutineOverflowMenu(
    routineName: String,
    onDeleteRoutine: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        KeepConfirmationDialog(
            title = stringResource(R.string.routine_delete_dialog_title),
            message = stringResource(R.string.routine_delete_dialog_message, routineName),
            confirmLabel = stringResource(R.string.routine_delete_dialog_confirm),
            dismissLabel = stringResource(R.string.routine_delete_dialog_cancel),
            confirmTone = KeepConfirmationTone.Critical,
            onConfirm = {
                showDeleteConfirmation = false
                onDeleteRoutine()
            },
            onDismiss = { showDeleteConfirmation = false },
        )
    }

    Box {
        KeepIconButton(onClick = { showMenu = true }) {
            Icon(
                painter = painterResource(R.drawable.outline_more_vert_24),
                contentDescription = stringResource(R.string.cd_routine_more_options),
                tint = KeepTheme.semanticColors.foreground.neutral,
            )
        }
        KeepMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            KeepMenuItem(
                text = stringResource(R.string.routine_delete_action),
                tone = KeepMenuItemTone.Critical,
                onClick = {
                    showMenu = false
                    showDeleteConfirmation = true
                },
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = painterResource(R.drawable.outline_delete_24),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun RoutineSheetFooter(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeepTheme.semanticColors.background.layerSheet)
            .navigationBarsPadding(),
    ) {
        KeepDivider()
        Box(
            modifier = Modifier.padding(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = 16.dp,
            ),
        ) {
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                enabled = enabled,
                bottomSpacing = false,
                onClick = onClick,
            )
        }
    }
}

/**
 * 동의를 거부한 채로 루틴을 저장하면, 잠금이 시작되는 몇 시간 뒤까지 사용자는 웹사이트가
 * 막힐 것이라고 믿는다. 거부한 자리에서 바로 말해주고 그 자리에서 다시 받을 수 있어야 한다.
 */
@Composable
private fun RoutineWebsiteConsentWarning(
    hasWebsiteTargets: Boolean,
    modifier: Modifier = Modifier,
) {
    val status by WebsiteBlockingRuntimeState.status.collectAsState()
    if (!hasWebsiteTargets || status != WebsiteBlockingStatus.ConsentDenied) return

    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
        }
    }

    WebsiteBlockingWarningRow(
        modifier = modifier,
        message = stringResource(R.string.routine_website_consent_denied),
        action = {
            // 경고 자체는 붉은 톤이지만 이 버튼은 되돌리는 행동이지 파괴적인 행동이 아니다.
            KeepTextButton(
                onClick = {
                    val consentIntent = VpnService.prepare(context)
                    if (consentIntent == null) {
                        WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
                    } else {
                        consentLauncher.launch(consentIntent)
                    }
                },
            ) {
                Text(text = stringResource(R.string.website_blocking_consent_retry))
            }
        },
    )
}

@Composable
private fun RoutineAppField(
    selectApps: Set<String>,
    selectWebDomains: Set<String>,
    onClick: () -> Unit,
) {
    val hasTargets = selectApps.isNotEmpty() || selectWebDomains.isNotEmpty()
    val value = when {
        !hasTargets -> null
        // 웹사이트가 없으면 지금까지의 표기를 그대로 둔다.
        selectWebDomains.isEmpty() -> stringResource(R.string.category_selected, selectApps.size)
        else -> stringResource(R.string.lock_targets_selected, selectApps.size, selectWebDomains.size)
    }

    KeepField(
        label = stringResource(R.string.routine_apps_label),
        requirement = KeepFieldRequirement.Required,
    ) {
        KeepInputButton(
            placeholder = stringResource(R.string.select_apps_to_lock),
            value = value,
            onClick = onClick,
            leadingContent = {
                Image(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(
                        if (hasTargets) {
                            R.drawable.shield
                        } else {
                            R.drawable.ic_question_face
                        },
                    ),
                    contentDescription = null,
                )
            },
            trailingContent = { RoutineInputTrailingIcon() },
        )
    }
}

@Composable
private fun RoutineTimeField(
    startTime: LocalTime,
    endTime: LocalTime,
    setStartTime: (LocalTime) -> Unit,
    setEndTime: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    var isShowStartTimePicker by remember { mutableStateOf(false) }
    var isShowEndTimePicker by remember { mutableStateOf(false) }
    val errorMessage = when {
        startTime == endTime -> stringResource(R.string.routine_same_time_message)
        routineDurationMinutes(startTime, endTime) < 15 ->
            stringResource(R.string.routine_minimum_duration_message)
        else -> null
    }

    if (isShowStartTimePicker) {
        RoutineTimePickerDialog(
            title = stringResource(R.string.start_time),
            time = startTime,
            onConfirm = {
                setStartTime(it)
                isShowStartTimePicker = false
            },
            onDismissRequest = { isShowStartTimePicker = false },
        )
    }
    if (isShowEndTimePicker) {
        RoutineTimePickerDialog(
            title = stringResource(R.string.end_time),
            time = endTime,
            onConfirm = {
                setEndTime(it)
                isShowEndTimePicker = false
            },
            onDismissRequest = { isShowEndTimePicker = false },
        )
    }

    KeepField(
        label = stringResource(R.string.routine_schedule_label),
        requirement = KeepFieldRequirement.Required,
        errorMessage = errorMessage,
    ) { isError ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoutineTimeInput(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.start_time),
                value = startTime.toTimeString(context),
                isError = isError,
                onClick = { isShowStartTimePicker = true },
            )
            RoutineTimeInput(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.end_time),
                value = endTime.toTimeString(context),
                isError = isError,
                onClick = { isShowEndTimePicker = true },
            )
        }
    }
}

@Composable
private fun RoutineTimeInput(
    label: String,
    value: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeepLabel(
            text = label,
            tone = if (isError) KeepLabelTone.Critical else KeepLabelTone.Muted,
            size = KeepLabelSize.Small,
            weight = KeepLabelWeight.Strong,
        )
        KeepInputButton(
            placeholder = label,
            value = value,
            isError = isError,
            onClick = onClick,
            trailingContent = { RoutineInputTrailingIcon() },
        )
    }
}

@Composable
private fun RoutineTimePickerDialog(
    title: String,
    time: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var pendingTime by remember(time) { mutableStateOf(time) }

    KeepDialog(onDismissRequest = onDismissRequest) {
        Text(
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
            text = title,
            color = KeepTheme.semanticColors.foreground.neutral,
            style = MaterialTheme.typography.titleMedium,
        )
        TimerPicker(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            onChangeTimerTime = { pendingTime = it },
            time = pendingTime,
        )
        KeepButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            text = stringResource(R.string.routine_protection_picker_confirm),
            bottomSpacing = false,
            onClick = { onConfirm(pendingTime) },
        )
    }
}

@Composable
private fun RoutineDayField(
    selectDays: List<DayOfWeek>,
    onSelectDay: (DayOfWeek) -> Unit,
) {
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    KeepField(
        label = stringResource(R.string.repeat_days),
        requirement = KeepFieldRequirement.Required,
        helperText = stringResource(R.string.routine_days_helper),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                KeepChip(
                    text = formatWeekdayShort(day, appLocale),
                    selected = day in selectDays,
                    variant = KeepChipVariant.OutlineWeak,
                    size = KeepChipSize.Small,
                    role = KeepChipRole.Toggle,
                    onClick = { onSelectDay(day) },
                )
            }
        }
    }
}

@Composable
private fun RoutineProtectionField(
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
    val helperText = if (changeLockHours == null) {
        stringResource(R.string.routine_protection_helper)
    } else {
        val context = LocalContext.current
        val lockStartTimeText = remember(startTime, changeLockHours) {
            val lockStart = startTime.toJavaLocalTime()
                .minusHours(changeLockHours.toLong())
            LocalTime(lockStart.hour, lockStart.minute).toTimeString(context)
        }
        stringResource(R.string.change_lock_description, lockStartTimeText)
    }
    val pickerItems = remember(offText) {
        listOf(offText) + (1..24).map { it.toString() }
    }
    val pickerState = rememberPickerState()

    if (showDialog) {
        KeepDialog(onDismissRequest = { showDialog = false }) {
            Text(
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
                text = stringResource(R.string.change_lock_title),
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(32.dp)
                        .background(
                            shape = RoundedCornerShape(8.dp),
                            color = KeepTheme.semanticColors.background.neutralWeak,
                        ),
                )
                Picker(
                    state = pickerState,
                    items = pickerItems,
                    startIndex = changeLockHours ?: 0,
                    visibleItemsCount = 5,
                    isInfinity = true,
                    color = KeepTheme.semanticColors.foreground.neutral,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    ),
                    textModifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Box(
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
            ) {
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.routine_protection_picker_confirm),
                    bottomSpacing = false,
                    onClick = {
                        val selected = pickerState.selectedItem
                        onChangeLockHoursChanged(
                            selected
                                .takeUnless { it == offText || it.isEmpty() }
                                ?.toIntOrNull(),
                        )
                        showDialog = false
                    },
                )
            }
        }
    }

    KeepField(
        label = stringResource(R.string.change_lock_title),
        helperText = helperText,
        helperTextTone = KeepFieldHelperTone.Muted,
    ) {
        KeepInputButton(
            placeholder = offText,
            value = selectedText,
            onClick = { showDialog = true },
            trailingContent = { RoutineInputTrailingIcon() },
        )
    }
}

@Composable
private fun RoutineInputTrailingIcon() {
    Icon(
        modifier = Modifier.size(16.dp),
        painter = painterResource(R.drawable.round_arrow_forward_ios_24),
        contentDescription = null,
    )
}

@Composable
private fun RoutineAppSelectionContent(
    modifier: Modifier = Modifier,
    selectApps: Set<String>,
    selectWebDomains: Set<String>,
    setSelectApps: (Set<String>) -> Unit,
    setSelectTargets: (Set<String>, Set<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    // 루틴은 화면 없이 시작되므로 그때는 시스템 동의창을 띄울 수 없다. 사용자가 웹사이트를
    // 고르는 지금이 동의를 받을 수 있는 마지막 지점이다.
    val context = LocalContext.current
    var pendingTargets by remember { mutableStateOf<Pair<Set<String>, Set<String>>?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val targets = pendingTargets ?: return@rememberLauncherForActivityResult
        pendingTargets = null
        if (result.resultCode != Activity.RESULT_OK) {
            // 거부해도 선택은 지운다. 다시 켤 수 있는 상태로 남기고, 왜 막히지 않는지는
            // 잠금이 시작될 때 배너가 설명한다.
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
        }
        setSelectTargets(targets.first, targets.second)
        onBackClick()
    }

    Column(modifier = modifier.fillMaxSize()) {
        KeepIconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.baseline_arrow_back_ios_24),
                contentDescription = stringResource(R.string.cd_navigate_back),
                tint = KeepTheme.semanticColors.foreground.neutral,
            )
        }
        CategoryBottomSheetContent(
            storeSelectApps = selectApps,
            websiteSelectionEnabled = BuildConfig.WEBSITE_BLOCKING_ENABLED,
            storeSelectedWebDomains = selectWebDomains,
            onCompleteTargets = { apps, domains ->
                val consentIntent = if (domains.isEmpty()) null else VpnService.prepare(context)
                if (consentIntent == null) {
                    setSelectTargets(apps, domains)
                    onBackClick()
                } else {
                    pendingTargets = apps to domains
                    consentLauncher.launch(consentIntent)
                }
            },
            onComplete = {
                setSelectApps(it)
                onBackClick()
            },
        )
    }
}
