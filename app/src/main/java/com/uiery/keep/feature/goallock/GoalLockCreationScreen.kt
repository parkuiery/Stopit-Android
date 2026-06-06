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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "목표 잠금 만들기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = "뒤로 가기",
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
            onPresetExam = { viewModel.setGoalName("시험 준비") },
            onPresetSns = { viewModel.setGoalName("SNS 줄이기") },
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
private fun GoalLockCreationContent(
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
    val selectedAppItems = buildGoalLockSelectedAppItems(
        selectedPackages = state.selectedApps,
        resolveLabel = { packageName -> appDisplayMetadataResolver.resolve(packageName).label },
    )

    Column(
        modifier = modifier,
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
                    text = "목표 이름",
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.goalName,
                    onValueChange = onGoalNameChange,
                    placeholder = { Text("예: 시험 준비, SNS 줄이기") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPresetExam) { Text("시험 준비") }
                    TextButton(onClick = onPresetSns) { Text("SNS 줄이기") }
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
                    text = "기간",
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "${state.startDate} ~ ${state.endDate}",
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSelectSevenDays) { Text("7일") }
                    OutlinedButton(onClick = onSelectFourteenDays) { Text("14일") }
                    OutlinedButton(onClick = onSelectThirtyDays) { Text("30일") }
                }
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = customDaysText,
                    onValueChange = { value ->
                        customDaysText = value.filter { it.isDigit() }.take(3)
                        customDaysText.toIntOrNull()?.let(onCustomDaysChange)
                    },
                    placeholder = { Text("직접 일수 입력: 예: 21") },
                    singleLine = true,
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = endDateText,
                    onValueChange = { value ->
                        endDateText = value.take(10)
                        runCatching { LocalDate.parse(endDateText) }.getOrNull()?.let(onEndDateChange)
                    },
                    placeholder = { Text("종료 날짜 입력: YYYY-MM-DD") },
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
                    text = "잠금 방식",
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSetAllDay) {
                        Text("하루종일 잠금")
                    }
                    OutlinedButton(onClick = onSetWeekdayEvening) {
                        Text("평일 저녁 잠금")
                    }
                }
                Text(
                    text = when (val mode = state.lockMode) {
                        GoalLockCreationLockMode.AllDay -> "현재 방식: 하루종일 잠금"
                        is GoalLockCreationLockMode.Scheduled -> "현재 방식: ${mode.repeatDays.size}일 ${mode.startTime}~${mode.endTime}"
                    },
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Text(
                    text = "선택 앱 ${state.selectedApps.size}개가 목표 잠금에 사용됩니다. 홈 선택을 다시 불러오거나 이 목표에서만 뺄 앱을 조정할 수 있어요.",
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = onReloadCurrentSelection) {
                    Text("홈 선택 다시 불러오기")
                }
                OutlinedButton(onClick = onSelectApps) {
                    Text("앱 선택 화면에서 조정")
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
                            Text("빼기")
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
            Text("목표 잠금 시작")
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
                    "앱 이름을 불러오지 못했어요"
                } else {
                    normalizedPackageName
                },
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<GoalLockSelectedAppUiItem> { it.description == "앱 이름을 불러오지 못했어요" }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName },
        )
