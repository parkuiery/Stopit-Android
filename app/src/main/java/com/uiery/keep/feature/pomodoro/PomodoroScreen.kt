package com.uiery.keep.feature.pomodoro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

    // 기능을 먼저 소개하고, 계속할지는 사용자가 정한다. 메뉴에서 "집중 세션 시작"을 누른 사람은
    // 이게 뭘 하는 기능인지 모르는 상태다. 설명 한 줄을 시작 화면에 얹는 것과, 설명을 읽고
    // **넘어갈지 말지 고르게 하는 것**은 다르다.
    //
    // 시트에서 이미 시작을 누르고 들어온 경로(autoStart)는 소개를 지나친다. 재사용 2탭 계약을
    // 소개 화면으로 깨면 안 된다.
    var showIntro by rememberSaveable { mutableStateOf(!autoStart) }

    // 고르는 일과 시작하는 일을 한 화면에 같이 두면, 시작하러 들어온 사람이 매번 선택지를
    // 지나가게 된다. 설정은 제 화면을 갖고, 시작 화면에는 결과와 버튼만 남는다.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 세션이 시작되면 설정 화면은 의미가 없다. 시스템 back 이 아니라 상태로 닫는다.
    if (showSettings && (uiState.isSessionRunning || uiState.isSessionFinished)) {
        showSettings = false
    }
    BackHandler(enabled = showSettings) { showSettings = false }

    val navigateBackLabel = stringResource(R.string.cd_navigate_back)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            KeepTopAppBar(
                title = {
                    // 진행·완료 화면은 본문이 이미 상태를 크게 말한다. 제목을 또 얹으면 같은
                    // 문장이 두 줄 겹치므로 시작·설정 화면에서만 제목을 쓴다.
                    if (!uiState.isSessionRunning && !uiState.isSessionFinished && !showIntro) {
                        Text(
                            text = stringResource(
                                if (showSettings) {
                                    R.string.pomodoro_settings_title
                                } else {
                                    R.string.pomodoro_setup_title
                                },
                            ),
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
                            when {
                                showSettings -> showSettings = false
                                uiState.isSessionFinished -> viewModel.finishAndLeave()
                                else -> onNavigateHome()
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
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            // 소개 화면만 여백을 스스로 관리한다 — 히어로 배경이 화면 끝까지 가야 한다.
            val gutter = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
            when {
                uiState.isLoading -> Unit
                uiState.isSessionFinished -> Box(gutter) { PomodoroCompleteContent(
                    state = uiState,
                    onRestart = viewModel::restartSession,
                    onLeave = viewModel::finishAndLeave,
                ) }
                uiState.isSessionRunning -> Box(gutter) { PomodoroRunningContent(
                    state = uiState,
                    onEnd = viewModel::showEndConfirm,
                ) }
                showIntro -> PomodoroIntroContent(
                    cycle = uiState.selectedCycle,
                    onNext = { showIntro = false },
                )
                showSettings -> Box(gutter) { PomodoroSettingsContent(
                    state = uiState,
                    onSelectCycle = viewModel::selectCycle,
                    onSelectCustom = viewModel::selectCustomCycle,
                    onChangeCustomFocus = viewModel::changeCustomFocusMinutes,
                    onChangeCustomShortBreak = viewModel::changeCustomShortBreakMinutes,
                    onChangeCustomLongBreak = viewModel::changeCustomLongBreakMinutes,
                    onChangeCustomCycles = viewModel::changeCustomCycles,
                    onToggleBlockDuringBreaks = viewModel::setBlockDuringBreaks,
                    onPickApps = onPickApps,
                    onDone = { showSettings = false },
                ) }
                else -> Box(gutter) { PomodoroSetupContent(
                    state = uiState,
                    onOpenSettings = { showSettings = true },
                    onPickApps = onPickApps,
                    onStart = { viewModel.startSession() },
                ) }
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

/**
 * 기능 소개. 이 화면을 지나야 시작 화면이 나온다.
 *
 * 설명을 시작 화면 위에 한 줄 얹는 것과, 설명을 읽고 **계속할지 고르게 하는 것**은 다르다.
 * 앞의 것은 시작 버튼 옆의 곁다리 문장이 되고, 뒤의 것은 사용자가 내리는 결정이 된다.
 *
 * 그 결정을 하게 하려면 동작 설명이 아니라 **이걸 쓰면 뭐가 좋은지**를 말해야 한다. 처음 쓴
 * 문구는 셋 다 작동 방식이었고, 그중 "휴식 중에도 막아요"는 혜택이 아니라 제약으로 읽혔다.
 * 지금은 그 제약을 뒤집어 이 기능의 가장 강한 판매 논리로 쓴다 — 쉬는 동안 폰이 열리지 않으니
 * 휴식이 딴짓으로 흘러가지 않는다.
 *
 * 숫자를 쓰지 않는다. "집중 25분"은 마지막에 쓴 사이클에 따라 달라지므로, 여기에 박아 두면
 * 50/10 을 쓰는 사람에게는 거짓말이 된다. 구체적인 길이는 다음 화면이 말한다.
 */
@Composable
private fun PomodoroIntroContent(cycle: PomodoroCycle, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 히어로는 화면 끝까지 가는 톤 배경을 깔고 가운데로 모은다. 토스뱅크 상품 페이지를
            // iPhone 12 규격으로 열어 보고 가져온 구조다 — 작은 아이브로우가 위, 큰 제목이
            // 아래, 그 아래 시각물, 전부 가운데 정렬, 섹션 전체가 브랜드 톤.
            //
            // 다만 토스는 아이브로우에 약속을 넣고 제목에 상품명을 넣는다. 상품명이 곧 파는
            // 물건이라 그렇다. 앱 안의 기능 소개에서는 약속이 더 센 말이므로 자리를 바꿔
            // 기능 이름을 위로, 약속을 큰 자리로 보낸다.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KeepTheme.semanticColors.background.brandWeak)
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 진행 화면이 쓰는 원형 링을 미리 보여준다. 설명은 아래 다이어그램이 하고, 이건
                // 분위기만 맡는다 — 무슨 앱에 들어와 있는지를 글자보다 먼저 말하는 자리다.
                val composition by rememberLottieComposition(
                    LottieCompositionSpec.RawRes(R.raw.pomodoro_intro),
                )
                LottieAnimation(
                    modifier = Modifier.size(130.dp),
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.pomodoro_intro_eyebrow),
                    color = KeepTheme.colors.surfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 크기를 로케일 자원에서 읽는다. CJK 는 같은 문장이 절반 길이로 끝나서 크게
                // 키워도 한두 줄이지만, 라틴 문자로 같은 크기를 쓰면 세 줄이 되면서 히어로가
                // 화면을 다 먹는다. 자세한 근거는 values/integers.xml 주석 참고.
                val titleSp = integerResource(R.integer.pomodoro_intro_title_sp).sp
                Text(
                    text = stringResource(R.string.pomodoro_intro_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSp,
                    lineHeight = titleSp * 1.35f,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pomodoro_intro_subtitle),
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // 순서가 곧 우선순위다. 이 앱이 다른 뽀모도로 타이머와 다른 지점(참지 않아도 된다)이
            // 먼저 오고, 그 다음이 이 기능만의 재해석(휴식도 막는 것은 제약이 아니라 혜택),
            // 마지막이 문턱을 낮추는 말이다.
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(modifier = Modifier.height(32.dp))
                PomodoroIntroPoint(
                    title = stringResource(R.string.pomodoro_intro_willpower_title),
                    description = stringResource(R.string.pomodoro_intro_willpower_description),
                )
                Spacer(modifier = Modifier.height(24.dp))
                PomodoroIntroPoint(
                    title = stringResource(R.string.pomodoro_intro_break_title),
                    description = stringResource(R.string.pomodoro_intro_break_description),
                )
                Spacer(modifier = Modifier.height(12.dp))
                PomodoroCycleTrack(cycle = cycle)
                Spacer(modifier = Modifier.height(24.dp))
                PomodoroIntroPoint(
                    title = stringResource(R.string.pomodoro_intro_start_title),
                    description = stringResource(R.string.pomodoro_intro_start_description),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.pomodoro_intro_cta),
                size = KeepButtonSize.Large,
                bottomSpacing = false,
                onClick = onNext,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PomodoroIntroPoint(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = KeepTheme.colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(
            text = description,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

/**
 * 한 세션이 어떻게 흘러가는지 보여주는 띠.
 *
 * 이 기능에서 가장 설명하기 어려운 것은 **집중과 휴식은 번갈아 오는데 차단은 끊기지 않는다**는
 * 관계다. 문장으로 쓰면 두 줄이 필요하고 그마저도 잘 안 읽히는데, 위아래로 겹친 두 줄짜리
 * 그림이면 한눈에 들어온다 — 위는 조각조각 나뉘어 있고 아래는 하나로 이어져 있다.
 *
 * 재생 헤드가 왼쪽에서 오른쪽으로 지나가며 위쪽 조각을 차례로 채운다. 정지된 그림이면 위쪽이
 * "지금 어디"인지 알 수 없어서 시간의 흐름이 읽히지 않는다.
 *
 * 길이는 실제로 고른 사이클의 비율을 쓴다. 25/5 를 고르면 집중 덩어리가 크고 휴식은 얇은 띠로
 * 보이는데, 그 비율 자체가 설명이다.
 */
@Composable
private fun PomodoroCycleTrack(cycle: PomodoroCycle) {
    // 마지막 집중 뒤에는 긴 휴식이 오고 세션이 끝난다.
    val segments = remember(cycle) {
        buildList {
            repeat(cycle.cycles) { index ->
                add(cycle.focusMinutes to true)
                add((if (index == cycle.cycles - 1) cycle.longBreakMinutes else cycle.shortBreakMinutes) to false)
            }
        }
    }
    val focusColor = KeepTheme.colors.primary
    val breakColor = KeepTheme.colors.onTertiaryContainer
    val transition = rememberInfiniteTransition(label = "pomodoro-cycle-track")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
        ),
        label = "playhead",
    )

    val totalMinutes = segments.sumOf { it.first }.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.semanticColors.background.layerDefault)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 어느 쪽이 집중이고 어느 쪽이 휴식인지 색만으로는 알 수 없다.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PomodoroTrackLegend(
                color = focusColor,
                label = stringResource(R.string.pomodoro_custom_focus_label),
            )
            PomodoroTrackLegend(
                color = breakColor,
                label = stringResource(R.string.pomodoro_intro_legend_break),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            var startFraction = 0f
            segments.forEach { (minutes, isFocus) ->
                val endFraction = startFraction + minutes / totalMinutes
                // 재생 헤드가 지나간 조각은 제 색으로, 아직인 조각은 옅게 둔다.
                val reached = progress >= startFraction
                Box(
                    modifier = Modifier
                        .weight(minutes.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            (if (isFocus) focusColor else breakColor)
                                .copy(alpha = if (reached) 1f else 0.18f),
                        ),
                )
                startFraction = endFraction
            }
        }

        // 위가 조각나 있는 동안 이 줄은 끊기지 않는다. 그것이 이 그림이 하는 말이다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(focusColor.copy(alpha = 0.22f)),
        )
    }
}

@Composable
private fun PomodoroTrackLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 12.sp,
        )
    }
}


@Composable
private fun PomodoroSetupContent(
    state: PomodoroUiState,
    onOpenSettings: () -> Unit,
    onPickApps: () -> Unit,
    onStart: () -> Unit,
) {
    // 한 화면에 질문 하나. 여기가 묻는 것은 "시작할까?" 뿐이다 — 무엇이 일어나는지 말하고 버튼
    // 하나를 준다. 고르는 일은 제 화면으로 갔다.
    //
    // 기본값은 이미 정해져 있고 직전에 쓴 사이클이 복원되므로 대부분은 그대로 쓴다. 시작하러
    // 들어온 사람에게 선택지를 먼저 내밀면, 아무것도 고르고 싶지 않은 사람까지 결정을 하게 된다.
    //
    // 본문은 스크롤하고 시작 버튼은 바닥에 고정한다. 큰 글꼴에서는 이 짧은 본문도 넘칠 수 있다.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // 기능 설명은 앞 화면이 이미 했다. 여기서 같은 문장을 되풀이하면 방금 읽은 것을 또
            // 읽히는 셈이다. 이 화면이 하는 말은 "이대로 갈까요?" 하나다.
            Text(
                text = stringResource(R.string.pomodoro_setup_confirm_title),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))
            // 바꿀 대상이 곧 누를 자리다. 요약 옆에 "설정 바꾸기"라는 맨 글자를 따로 띄워 두면
            // 눌리는 것처럼 보이지도 않고, 그 글자와 요약이 같은 것을 가리킨다는 것도 안 읽힌다.
            //
            // 고른 것은 "집중 25분"이지만 실제로 예약되는 잠금은 2시간 10분이다. 그 숫자를 모르고
            // 시작하면 예측 가능한 잠금이라는 이 앱의 약속이 깨지므로, 카드가 그것부터 말한다.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KeepTheme.semanticColors.background.layerDefault)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.pomodoro_setup_customize),
                        onClick = onOpenSettings,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.pomodoro_cycle_summary_title,
                            state.selectedCycle.focusMinutes,
                            state.selectedCycle.shortBreakMinutes,
                        ),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    PomodoroTotalLengthSummary(cycle = state.selectedCycle)
                }
                Icon(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(R.drawable.round_arrow_forward_ios_24),
                    contentDescription = null,
                    tint = KeepTheme.colors.onTertiaryContainer,
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
 * 세션 설정. 시작 화면의 "설정 바꾸기"로 들어온다.
 *
 * **여기에 시작 버튼을 두지 않는다.** 이 화면이 하는 일은 고르는 것 하나고, 시작은 돌아가서 한다.
 * 둘을 한 화면에 같이 두면 "고르는 중"과 "시작하는 중"이 섞여 무엇을 누르는 자리인지 흐려진다.
 *
 * 상단 요약은 고를 때마다 같이 움직인다. 사이클을 바꾸는 건 총 잠금을 2시간 10분에서 4시간 10분으로
 * 바꾸는 결정이라, 그 결과가 선택과 같은 화면에 보여야 한다.
 */
@Composable
private fun PomodoroSettingsContent(
    state: PomodoroUiState,
    onSelectCycle: (PomodoroCycle) -> Unit,
    onSelectCustom: () -> Unit,
    onChangeCustomFocus: (Int) -> Unit,
    onChangeCustomShortBreak: (Int) -> Unit,
    onChangeCustomLongBreak: (Int) -> Unit,
    onChangeCustomCycles: (Int) -> Unit,
    onToggleBlockDuringBreaks: (Boolean) -> Unit,
    onPickApps: () -> Unit,
    onDone: () -> Unit,
) {
    // 커스텀 카드를 펼치면 스테퍼 네 개가 더해져 내용이 화면보다 길어진다. 한 덩어리로 두면 완료
    // 버튼이 화면 밖으로 밀려 **커스텀 세션을 확정할 수 없다.**
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(
                    R.string.pomodoro_cycle_summary_title,
                    state.selectedCycle.focusMinutes,
                    state.selectedCycle.shortBreakMinutes,
                ),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            PomodoroTotalLengthSummary(cycle = state.selectedCycle)

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
                    text = stringResource(
                        R.string.pomodoro_setup_targets_summary,
                        state.selectedAppCount,
                    ),
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

        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.pomodoro_settings_done),
            size = KeepButtonSize.Large,
            bottomSpacing = false,
            onClick = onDone,
        )
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
