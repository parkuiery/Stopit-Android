package com.uiery.keep.feature.pomodoro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonSize
import com.uiery.kds.KeepButtonVariant
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepIconButton
import com.uiery.kds.KeepCircularProgressIndicator
import com.uiery.kds.KeepSelectableCard
import com.uiery.kds.KeepSwitch
import com.uiery.kds.KeepTopAppBar
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.pomodoro.PomodoroPhase
import com.uiery.keep.domain.pomodoro.PomodoroPolicy
import com.uiery.keep.domain.pomodoro.PomodoroCycle
import com.uiery.keep.domain.pomodoro.PomodoroPresetKind
import com.uiery.keep.ui.component.SetupStepper
import com.uiery.keep.util.formatMinuteSecondCountdown
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 집중 세션 화면.
 *
 * 집중과 휴식이 같은 화면을 쓴다. 구간이 바뀌면 색과 문구가 바뀔 뿐 사용자가 있는 자리는 그대로다.
 * **휴식 중에도 차단은 유지된다**는 사실을 화면이 직접 말하는 것이 이 화면의 가장 중요한 일이다.
 */
@Composable
internal fun PomodoroScreen(
    modifier: Modifier = Modifier,
    autoStart: Boolean = false,
    viewModel: PomodoroViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
    onPickApps: () -> Unit,
    onStartSessionService: () -> Unit,
    onStopSessionService: () -> Unit,
) {
    val uiState by viewModel.collectAsState()

    viewModel.collectSideEffect { effect ->
        when (effect) {
            PomodoroSideEffect.StartSessionService -> onStartSessionService()
            // 잠금을 못 세운 경우다. 현재는 이미 다른 타이머 잠금이 도는 상황이 유일한 원인이고,
            // 그때는 홈이 그 잠금을 보여주는 자리이므로 홈으로 돌려보낸다.
            PomodoroSideEffect.StartBlocked -> onNavigateHome()
            PomodoroSideEffect.StopSessionService -> onStopSessionService()
            PomodoroSideEffect.NavigateHome -> onNavigateHome()
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // 시트에서 이미 시작을 누르고 들어왔다면 여기서 한 번 더 묻지 않는다.
    //
    // 지난 세션의 완료 화면이 아직 저장소에 남아 있을 수 있다. 그대로 두면 "집중 시작"을 눌렀는데
    // 지난 세션 결과가 뜨므로, 먼저 치우고 시작한다.
    LaunchedEffect(autoStart, uiState.isLoading, uiState.isSessionRunning) {
        if (autoStart && !uiState.isLoading && !uiState.isSessionRunning) {
            viewModel.startFresh()
        }
    }

    LaunchedEffect(uiState.isSessionRunning) {
        while (uiState.isSessionRunning) {
            delay(1_000)
            viewModel.tick()
        }
    }

    if (uiState.isEndConfirmVisible) {
        KeepConfirmationDialog(
            title = stringResource(R.string.pomodoro_end_confirm_title),
            // 아직 한 번도 못 끝낸 세션에 "완료한 0회는 기록에 남아요"라고 말하면 문장이
            // 성립하지 않고, 아무것도 못 했다는 사실만 남는다. 잃는 게 없다는 쪽으로 말한다.
            message = if (uiState.completedFocusCount == 0) {
                stringResource(R.string.pomodoro_end_confirm_message_none)
            } else {
                stringResource(
                    R.string.pomodoro_end_confirm_message,
                    uiState.completedFocusCount,
                )
            },
            confirmLabel = stringResource(R.string.pomodoro_end_confirm_end),
            dismissLabel = stringResource(R.string.pomodoro_end_confirm_continue),
            onConfirm = viewModel::confirmEnd,
            onDismiss = viewModel::hideEndConfirm,
        )
    }

    val navigateBackLabel = stringResource(R.string.cd_navigate_back)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KeepTopAppBar(
                title = {
                    // 진행·완료 화면은 본문이 이미 상태를 크게 말한다. 제목을 또 얹으면 같은
                    // 문장이 두 줄 겹치므로 설정 화면에서만 제목을 쓴다.
                    if (!uiState.isSessionRunning && !uiState.isSessionFinished) {
                        Text(
                            text = stringResource(R.string.pomodoro_setup_title),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                    }
                },
                navigationIcon = {
                    KeepIconButton(
                        onClick = {
                            // 끝난 세션을 남겨 두고 나가면 다음 진입에서 완료 화면이 다시 뜬다.
                            // 나가는 순간 치우고, 진행 중 세션은 그대로 두고 홈으로만 돌아간다.
                            if (uiState.isSessionFinished) {
                                viewModel.finishAndLeave()
                            } else {
                                onNavigateHome()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = navigateBackLabel,
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isLoading -> Unit
                uiState.isSessionFinished -> PomodoroCompleteContent(
                    state = uiState,
                    onRestart = viewModel::restartSession,
                    onLeave = viewModel::finishAndLeave,
                )
                uiState.isSessionRunning -> PomodoroRunningContent(
                    state = uiState,
                    onEnd = viewModel::showEndConfirm,
                )
                else -> PomodoroSetupContent(
                    state = uiState,
                    onSelectCycle = viewModel::selectCycle,
                    onSelectCustom = viewModel::selectCustomCycle,
                    onChangeCustomFocus = viewModel::changeCustomFocusMinutes,
                    onChangeCustomShortBreak = viewModel::changeCustomShortBreakMinutes,
                    onChangeCustomLongBreak = viewModel::changeCustomLongBreakMinutes,
                    onChangeCustomCycles = viewModel::changeCustomCycles,
                    onToggleBlockDuringBreaks = viewModel::setBlockDuringBreaks,
                    onPickApps = onPickApps,
                    onStart = { viewModel.startSession() },
                )
            }
        }
    }
}

@Composable
private fun PomodoroRunningContent(
    state: PomodoroUiState,
    onEnd: () -> Unit,
) {
    val phase = state.phase ?: PomodoroPhase.Focus
    val isFocus = phase == PomodoroPhase.Focus
    // 앰버는 희소 토큰이다. "지금 보호 중"인 집중 구간만 가져간다. 휴식은 중립색으로 두어
    // 두 구간이 색으로도 구분되게 하고, 구분은 아래 텍스트가 다시 한 번 말한다.
    val accent = if (isFocus) KeepTheme.colors.primary else KeepTheme.colors.surface
    val phaseLabel = stringResource(phase.labelResId())
    val cycleProgress = stringResource(
        R.string.pomodoro_cycle_progress,
        state.session?.cycleIndex ?: 1,
        state.cyclesPerSession,
    )
    val remainingClock = formatMinuteSecondCountdown(state.remainingSeconds)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = phaseLabel,
            color = if (isFocus) KeepTheme.colors.onPrimaryContainer else KeepTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        // 이 카테고리의 지배적 패턴은 큰 원형 링과 그 안의 카운트다운이다. 가로 막대는 남은 시간을
        // 같은 정확도로 보여주지만, 사용자가 다른 집중 앱에서 익힌 모양이 아니다.
        Box(
            modifier = Modifier.size(POMODORO_RING_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            KeepCircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                progress = { state.remainingFraction },
                color = accent,
                strokeWidth = POMODORO_RING_STROKE,
                strokeCap = StrokeCap.Round,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    modifier = Modifier.semantics {
                        contentDescription = "$phaseLabel, $remainingClock, $cycleProgress"
                    },
                    text = remainingClock,
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                )
                Text(
                    text = stringResource(R.string.pomodoro_remaining_label, state.phaseTotalMinutes),
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = cycleProgress,
            color = KeepTheme.colors.surfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        state.nextFocusCycleIndex?.let { next ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pomodoro_next_focus, next),
                color = KeepTheme.colors.surface,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pomodoro_today_focus_count, state.todayFocusCount),
            color = KeepTheme.colors.surface,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(28.dp))
        PomodoroBlockingNotice(state = state, isBreak = phase.isBreak)

        Spacer(modifier = Modifier.height(16.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.pomodoro_end_session),
            variant = KeepButtonVariant.NeutralOutline,
            size = KeepButtonSize.Large,
            bottomSpacing = false,
            onClick = onEnd,
        )
    }
}

/**
 * 휴식 중에도 차단이 유지된다는 사실을 사용자가 버그로 읽지 않게 하는 유일한 장치다.
 * 보조 문구가 아니라 지면을 차지하는 카드로 둔다.
 */
@Composable
private fun PomodoroBlockingNotice(
    state: PomodoroUiState,
    isBreak: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isBreak) {
            Text(
                text = stringResource(R.string.pomodoro_break_blocking_title),
                color = KeepTheme.colors.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(
                    R.string.pomodoro_break_blocking_description,
                    state.selectedAppCount,
                ),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
            Text(
                text = stringResource(R.string.pomodoro_break_auto_continue),
                color = KeepTheme.colors.surface,
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = stringResource(R.string.pomodoro_blocking_summary, state.selectedAppCount),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PomodoroSetupContent(
    state: PomodoroUiState,
    onSelectCycle: (PomodoroCycle) -> Unit,
    onSelectCustom: () -> Unit,
    onChangeCustomFocus: (Int) -> Unit,
    onChangeCustomShortBreak: (Int) -> Unit,
    onChangeCustomLongBreak: (Int) -> Unit,
    onChangeCustomCycles: (Int) -> Unit,
    onToggleBlockDuringBreaks: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onStart: () -> Unit,
) {
    // 제목은 상단 바가 들고 있다. 본문에서 한 번 더 쓰면 같은 문장이 두 줄 겹쳐 보인다.
    //
    // 고르는 내용은 스크롤하고 시작 버튼은 바닥에 고정한다. 커스텀 카드를 펼치면 스테퍼 세 개가
    // 더해져 내용이 화면보다 길어지는데, 한 덩어리로 두면 버튼이 화면 밖으로 밀려 **커스텀
    // 세션을 아예 시작할 수 없다.** 큰 글꼴에서는 프리셋만으로도 넘칠 수 있다.
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pomodoro_setup_description),
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pomodoro_setup_cycle_label),
            color = KeepTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        PomodoroCycleOptions(
            state = state,
            onSelectCycle = onSelectCycle,
            onSelectCustom = onSelectCustom,
            onChangeCustomFocus = onChangeCustomFocus,
            onChangeCustomShortBreak = onChangeCustomShortBreak,
            onChangeCustomLongBreak = onChangeCustomLongBreak,
            onChangeCustomCycles = onChangeCustomCycles,
        )

        Spacer(modifier = Modifier.height(20.dp))
        // 뽀모도로 카테고리의 기본 동작은 "휴식엔 열림"이다. 이 앱을 고른 이유가 차단이라 기본은
        // 계속 막는 쪽으로 두되, 쉬는 동안 열어 두고 싶은 사용자에게 끌 수 있는 길을 준다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.pomodoro_block_during_breaks_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.pomodoro_block_during_breaks_description),
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
            }
            KeepSwitch(
                checked = state.blockDuringBreaks,
                onCheckedChange = onToggleBlockDuringBreaks,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.pomodoro_setup_targets_title),
            color = KeepTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                // 읽기만 되는 줄이면 대상을 바꾸려고 홈까지 나갔다 와야 한다. 여기서 바로 간다.
                .clickable(onClick = onPickApps)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.pomodoro_setup_targets_summary, state.selectedAppCount),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
            Text(
                text = stringResource(R.string.pomodoro_setup_choose_apps),
                color = KeepTheme.colors.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }

    }

        if (state.selectedAppCount == 0) {
            // 비활성 버튼만 두면 왜 못 누르는지도, 어디서 고치는지도 알 수 없는 막다른 길이 된다.
            Text(
                text = stringResource(R.string.pomodoro_setup_no_apps),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.pomodoro_setup_choose_apps),
                size = KeepButtonSize.Large,
                bottomSpacing = false,
                onClick = onPickApps,
            )
        } else {
            // 고른 것은 "집중 25분"이지만 실제로 예약되는 잠금은 2시간 10분이다. 그 숫자를 커밋
            // 직전에 말하지 않으면 사용자는 자기가 얼마를 거는지 모르고 시작한다.
            PomodoroTotalLengthSummary(cycle = state.selectedCycle)
            Spacer(modifier = Modifier.height(10.dp))
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.pomodoro_setup_start),
                size = KeepButtonSize.Large,
                bottomSpacing = false,
                onClick = onStart,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * 이 세션이 실제로 잠그는 전체 시간.
 *
 * `50/10` 을 고르면 4시간 10분이다. 예측 가능한 잠금 상태가 이 앱의 정체성이므로, 시작 버튼 바로
 * 위에서 그 숫자를 말한다.
 */
@Composable
private fun PomodoroTotalLengthSummary(cycle: PomodoroCycle) {
    val total = PomodoroPolicy.totalDuration(cycle)
    val hours = total.toHours().toInt()
    val minutes = (total.toMinutes() % 60).toInt()
    val text = if (hours > 0) {
        stringResource(
            R.string.pomodoro_setup_total_summary_hours,
            cycle.cycles,
            hours,
            minutes,
        )
    } else {
        stringResource(
            R.string.pomodoro_setup_total_summary_minutes,
            cycle.cycles,
            minutes,
        )
    }
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        color = KeepTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
}

/**
 * 사이클 길이 고르기.
 *
 * 처음에는 [KeepButton] 두 개를 세워 두고 설명을 그 아래에 흘렸는데, 화면에 버튼이 세 개
 * (프리셋 둘 + 시작 하나) 서면서 무엇이 "고르는 것"이고 무엇이 "실행하는 것"인지 읽히지 않았다.
 * DESIGN.md 의 컴포넌트 표가 라디오형 선택면으로 지정한 [KeepSelectableCard] 를 쓴다. 설명이
 * 카드 안으로 들어가 선택지와 붙고, 역할(RadioButton)과 선택 상태가 접근성에도 그대로 전달된다.
 *
 * 커스텀은 네 번째 화면을 만들지 않는다. 고른 카드 안에서 바로 길이를 조절하게 해서 선택과
 * 조절이 같은 자리에 있게 한다.
 */
@Composable
private fun PomodoroCycleOptions(
    state: PomodoroUiState,
    onSelectCycle: (PomodoroCycle) -> Unit,
    onSelectCustom: () -> Unit,
    onChangeCustomFocus: (Int) -> Unit,
    onChangeCustomShortBreak: (Int) -> Unit,
    onChangeCustomLongBreak: (Int) -> Unit,
    onChangeCustomCycles: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PomodoroCycle.presets.forEach { cycle ->
            val title = stringResource(cycle.kind.titleResId())
            val description = stringResource(cycle.kind.descriptionResId())
            KeepSelectableCard(
                title = title,
                description = description,
                selected = state.selectedCycle.kind == cycle.kind,
                onClick = { onSelectCycle(cycle) },
                contentDescription = "$title, $description",
            )
        }

        val customTitle = stringResource(R.string.pomodoro_preset_custom_title)
        val customDescription = stringResource(R.string.pomodoro_preset_custom_description)
        KeepSelectableCard(
            title = customTitle,
            description = customDescription,
            selected = state.isCustomSelected,
            onClick = onSelectCustom,
            contentDescription = "$customTitle, $customDescription",
            supportingContent = if (state.isCustomSelected) {
                {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PomodoroMinutesStepper(
                            label = stringResource(R.string.pomodoro_custom_focus_label),
                            minutes = state.customFocusMinutes,
                            range = PomodoroCycle.FOCUS_MINUTES_RANGE,
                            onChange = onChangeCustomFocus,
                        )
                        PomodoroMinutesStepper(
                            label = stringResource(R.string.pomodoro_custom_short_break_label),
                            minutes = state.customShortBreakMinutes,
                            range = PomodoroCycle.SHORT_BREAK_MINUTES_RANGE,
                            onChange = onChangeCustomShortBreak,
                        )
                        PomodoroMinutesStepper(
                            label = stringResource(R.string.pomodoro_custom_long_break_label),
                            minutes = state.customLongBreakMinutes,
                            range = PomodoroCycle.LONG_BREAK_MINUTES_RANGE,
                            onChange = onChangeCustomLongBreak,
                        )
                        PomodoroCountStepper(
                            label = stringResource(R.string.pomodoro_custom_cycles_label),
                            count = state.customCycles,
                            range = PomodoroCycle.CYCLES_RANGE,
                            onChange = onChangeCustomCycles,
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * 목표 잠금의 커스텀 기간이 쓰는 [SetupStepper] 를 그대로 쓴다. 같은 성격의 입력이 앱 안에서
 * 두 가지 모양을 갖지 않게 한다.
 *
 * 도메인은 1분 단위까지 허용하지만 스테퍼는 [CUSTOM_MINUTES_STEP] 단위로만 움직인다.
 */
@Composable
private fun PomodoroMinutesStepper(
    label: String,
    minutes: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val valueLabel = stringResource(R.string.pomodoro_custom_minutes_value, minutes)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 13.sp,
        )
        SetupStepper(
            valueLabel = valueLabel,
            onDecrement = { onChange(-CUSTOM_MINUTES_STEP) },
            onIncrement = { onChange(CUSTOM_MINUTES_STEP) },
            decrementEnabled = minutes - CUSTOM_MINUTES_STEP >= range.first,
            incrementEnabled = minutes + CUSTOM_MINUTES_STEP <= range.last,
            contentDescription = "$label, $valueLabel",
        )
    }
}

/** 분이 아니라 횟수를 세는 스테퍼. 한 칸은 1회다. */
@Composable
private fun PomodoroCountStepper(
    label: String,
    count: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    val valueLabel = stringResource(R.string.pomodoro_custom_cycles_value, count)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 13.sp,
        )
        SetupStepper(
            valueLabel = valueLabel,
            onDecrement = { onChange(-1) },
            onIncrement = { onChange(1) },
            decrementEnabled = count - 1 >= range.first,
            incrementEnabled = count + 1 <= range.last,
            contentDescription = "$label, $valueLabel",
        )
    }
}

@Composable
private fun PomodoroCompleteContent(
    state: PomodoroUiState,
    onRestart: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 5초 만에 끊은 세션에 "세션을 마쳤어요 / 오늘 기록에 남았어요"라고 말하면 사실과
        // 어긋난다. 끝까지 돈 세션과 사용자가 끊은 세션은 다른 이야기다.
        Text(
            text = stringResource(
                if (state.isSessionEndedEarly) {
                    R.string.pomodoro_ended_title
                } else {
                    R.string.pomodoro_complete_title
                },
            ),
            color = KeepTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(
                // 한 번도 못 끝냈으면 "기록에 남았어요"라고 할 것이 없다.
                if (state.completedFocusCount == 0) {
                    R.string.pomodoro_end_confirm_message_none
                } else {
                    R.string.pomodoro_complete_subtitle
                },
            ),
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PomodoroCompleteStat(
                value = state.completedFocusCount.toString(),
                label = stringResource(R.string.pomodoro_complete_focus_count_label),
                highlighted = true,
            )
            PomodoroCompleteStat(
                value = stringResource(R.string.pomodoro_complete_minutes, state.completedFocusMinutes),
                label = stringResource(R.string.pomodoro_complete_focus_duration_label),
                highlighted = false,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.pomodoro_complete_restart),
            size = KeepButtonSize.Large,
            onClick = onRestart,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.pomodoro_complete_home),
            variant = KeepButtonVariant.Ghost,
            size = KeepButtonSize.Large,
            onClick = onLeave,
        )
    }
}

@Composable
private fun PomodoroCompleteStat(
    value: String,
    label: String,
    highlighted: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = value,
            color = if (highlighted) {
                KeepTheme.colors.onPrimaryContainer
            } else {
                KeepTheme.colors.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
        )
        Text(
            text = label,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 13.sp,
        )
    }
    Spacer(modifier = Modifier.width(0.dp))
}

private fun PomodoroPhase.labelResId(): Int = when (this) {
    PomodoroPhase.Focus -> R.string.pomodoro_phase_focus
    PomodoroPhase.ShortBreak -> R.string.pomodoro_phase_short_break
    PomodoroPhase.LongBreak -> R.string.pomodoro_phase_long_break
}

private fun PomodoroPresetKind.titleResId(): Int = when (this) {
    PomodoroPresetKind.Focus25 -> R.string.pomodoro_preset_25_title
    PomodoroPresetKind.Focus50 -> R.string.pomodoro_preset_50_title
    PomodoroPresetKind.Custom -> R.string.pomodoro_preset_custom_title
}

private fun PomodoroPresetKind.descriptionResId(): Int = when (this) {
    PomodoroPresetKind.Focus25 -> R.string.pomodoro_preset_25_description
    PomodoroPresetKind.Focus50 -> R.string.pomodoro_preset_50_description
    PomodoroPresetKind.Custom -> R.string.pomodoro_preset_custom_description
}

/** 카운트다운을 감싸는 링. 48sp 숫자와 남은 시간 설명이 안쪽에 들어갈 만큼 잡았다. */
private val POMODORO_RING_SIZE = 240.dp

private val POMODORO_RING_STROKE = 12.dp
