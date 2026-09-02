package com.uiery.keep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.analytics.toMetadata
import com.uiery.keep.lockscreen.LockScreenEntry
import com.uiery.keep.domain.parentmode.ParentModeBlockReason
import com.uiery.keep.lockscreen.LockScreenMode
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.util.formatMinuteSecondCountdown
import com.uiery.keep.service.emergencyUnlockActionUiState
import com.uiery.keep.ui.component.CountDownContent
import com.uiery.keep.ui.component.EmergencyUnlockBottomSheetContent
import com.uiery.keep.ui.component.RepeatBlockRoutineSuggestionCard
import com.uiery.keep.util.rememberAppDisplayMetadataResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockScreen(
    modifier: Modifier = Modifier,
    packageName: String,
    blockSource: String,
    routineId: String?,
    goalLockId: String?,
    viewModel: BlockViewModel = hiltViewModel(),
    onClose: () -> Unit,
    onOpenRoutineSuggestion: (RepeatBlockRoutineSuggestion) -> Unit = {},
) {
    val appDisplayMetadataResolver = rememberAppDisplayMetadataResolver()
    val uiState by viewModel.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val emergencyUnlockSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appName = remember(packageName, appDisplayMetadataResolver) {
        appDisplayMetadataResolver.resolve(packageName).label
    }
    val lockScreenEntry = remember(packageName, blockSource, routineId, goalLockId) {
        LockScreenEntry.fromBlockActivity(
            packageName = packageName,
            blockSource = blockSource,
            routineId = routineId,
            goalLockId = goalLockId,
        )
    }

    LaunchedEffect(lockScreenEntry) {
        viewModel.syncManualTimedLockReentry(lockScreenEntry)
        viewModel.syncParentModeBlockReason(lockScreenEntry)
        viewModel.syncPomodoroBlockContext(lockScreenEntry.blockSource)
        viewModel.trackBlockShown(lockScreenEntry)
    }

    LaunchedEffect(uiState.timedLockDeadline) {
        val deadline = uiState.timedLockDeadline ?: return@LaunchedEffect
        while (true) {
            val remainingMillis = deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                onClose()
                return@LaunchedEffect
            }
            delay(remainingMillis.coerceAtMost(1_000L))
        }
    }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is BlockSideEffect.UnlockCompleted,
            is BlockSideEffect.TimedLockExpired -> onClose()
            is BlockSideEffect.NavigateRoutineWithRepeatBlockPrefill -> onOpenRoutineSuggestion(effect.suggestion)
        }
    }

    if (uiState.isShowEmergencyUnlockSheet) {
        KeepModalBottomSheet(
            sheetState = emergencyUnlockSheetState,
            onDismissRequest = viewModel::hideEmergencyUnlockSheet,
        ) {
            EmergencyUnlockBottomSheetContent(
                blockedApps = setOf(packageName),
                durationOptions = uiState.emergencyUnlockDurationOptions,
                reasonStepEnabled = uiState.emergencyUnlockReasonRequired,
                countdownEnabled = uiState.emergencyUnlockCountdownEnabled,
                countdownSeconds = uiState.emergencyUnlockCountdownSeconds,
                onStepViewed = viewModel::trackEmergencyUnlockStepViewed,
                onValidationBlocked = viewModel::trackEmergencyUnlockValidationBlocked,
                onCancelled = viewModel::trackEmergencyUnlockCancelled,
                onUnlock = { reason, customReason, apps, duration ->
                    viewModel.emergencyUnlock(reason, customReason, apps, duration)
                    coroutineScope.launch {
                        emergencyUnlockSheetState.hide()
                    }.invokeOnCompletion {
                        if (!emergencyUnlockSheetState.isVisible) {
                            viewModel.hideEmergencyUnlockSheet()
                        }
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        emergencyUnlockSheetState.hide()
                    }.invokeOnCompletion {
                        if (!emergencyUnlockSheetState.isVisible) {
                            viewModel.hideEmergencyUnlockSheet()
                        }
                    }
                },
            )
        }
    }

    BlockScreenContent(
        modifier = modifier,
        appName = appName,
        blockMode = lockScreenEntry.mode,
        uiState = uiState,
        onShowEmergencyUnlock = viewModel::showEmergencyUnlockSheet,
        onOpenRoutineSuggestion = viewModel::openRepeatBlockRoutineSuggestion,
        onDismissRoutineSuggestion = viewModel::dismissRepeatBlockRoutineSuggestion,
        onClose = onClose,
    )
}

@Composable
internal fun BlockScreenContent(
    appName: String,
    blockMode: LockScreenMode = LockScreenMode.ManualKeep,
    uiState: BlockUiState,
    onShowEmergencyUnlock: () -> Unit,
    onOpenRoutineSuggestion: () -> Unit = {},
    onDismissRoutineSuggestion: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showBannerAd: Boolean = true,
) {
    BackHandler(enabled = true) {
        // Keep the protection surface in front. The explicit close CTA remains the
        // allowed way to leave the blocked app by sending the user Home.
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KeepTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showBannerAd) {
            // 배너는 흐름 안의 형제로 둔다. 겹쳐 놓으면 아래 콘텐츠가 배너 위에 그려져,
            // 세로 가운데 정렬된 아이콘이 배너를 뚫고 올라간다.
            TrackedBannerAd(
                modifier = Modifier.fillMaxWidth(),
                // 이 배너만 화면 위쪽에 놓인다. 위 여백은 콘텐츠와의 간격이 아니라 화면 끝과의
                // 간격이 되어 배너가 아래로 밀린다.
                contentSeparation = 0.dp,
                metadata = AdPlacement.BlockTop.toMetadata(
                    screenName = KeepAnalyticsScreen.BLOCK,
                    screenContext = "blocked_app",
                ),
            )
        }
        // 첫 차단 안내·카운트다운·루틴 사유가 함께 붙으면 문구 영역이 화면을 넘긴다. 넘칠 때
        // 잘리면 사용자는 왜 막혔는지 읽지 못한다. 넘치는 쪽은 문구고, 아래 행동 묶음은 어떤
        // 경우에도 남아야 한다. 시스템 뒤로가기가 막혀 있어 그 버튼이 유일한 출구다.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // 짧을 때는 화면을 채워 세로 가운데 정렬이 살아 있고, 길어지면 그만큼
                    // 늘어나 스크롤된다.
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 20.dp)
                    .testTag("block_screen_copy_area"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier
                        .sizeIn(
                            minHeight = 120.dp,
                            minWidth = 120.dp,
                        )
                        .clip(
                            RoundedCornerShape(12.dp)
                        ),
                    painter = painterResource(id = R.drawable.kepp_icon),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_title),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    fontSize = 32.sp,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_message, appName),
                    textAlign = TextAlign.Center,
                    color = KeepTheme.colors.surfaceVariant,
                )
                if (blockMode == LockScreenMode.Routine) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KeepTheme.colors.onSecondary)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        text = stringResource(id = R.string.block_screen_routine_active_reason),
                        textAlign = TextAlign.Center,
                        color = KeepTheme.colors.surfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                // Whoever is looking at this did not set the session up and has no other way to
                // find out why the phone stopped, so the reason is said plainly and without blame.
                uiState.parentModeBlockReason?.let { reason ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KeepTheme.colors.onSecondary)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("block_screen_parent_mode_reason"),
                        text = stringResource(
                            id = when (reason) {
                                ParentModeBlockReason.AllowedAppsOnly ->
                                    R.string.block_screen_parent_mode_allowed_apps_reason
                                ParentModeBlockReason.TimeExpired ->
                                    R.string.block_screen_parent_mode_expired_reason
                            },
                        ),
                        textAlign = TextAlign.Center,
                        color = KeepTheme.colors.surfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                // 집중 세션이 막은 화면이다. 남은 시간과 사이클 위치를 여기서 말하지 않으면
                // 사용자는 세션 화면으로 돌아가야만 자기가 어디쯤인지 알 수 있다.
                uiState.pomodoroBlockContext?.let { context ->
                    Spacer(modifier = Modifier.height(12.dp))
                    val remaining = formatMinuteSecondCountdown(context.remainingSeconds)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KeepTheme.colors.onSecondary)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("block_screen_pomodoro_context"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (context.isBreak) {
                                    R.string.pomodoro_block_break_title
                                } else {
                                    R.string.pomodoro_block_title
                                },
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            // 휴식 문구가 이 기능에서 가장 중요한 카피다. "쉬는 중인데 왜 안
                            // 열리지"라는 첫 반응에 대한 답이 여기 있어야 한다.
                            text = if (context.isBreak) {
                                stringResource(R.string.pomodoro_block_break_description, remaining)
                            } else {
                                stringResource(
                                    R.string.pomodoro_block_description,
                                    context.cycleIndex,
                                    remaining,
                                )
                            },
                            textAlign = TextAlign.Center,
                            color = KeepTheme.colors.surfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            text = stringResource(
                                R.string.pomodoro_cycle_progress,
                                context.cycleIndex,
                                context.cyclesPerSession,
                            ),
                            color = KeepTheme.colors.surface,
                            fontSize = 12.sp,
                        )
                    }
                }
                uiState.timedLockDeadline?.let { deadline ->
                    Spacer(modifier = Modifier.height(20.dp))
                    CountDownContent(
                        modifier = Modifier.testTag("block_screen_timed_lock_countdown"),
                        endTime = deadline,
                    )
                }
                if (uiState.showFirstCoreActionFeedback) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(KeepTheme.colors.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        text = stringResource(id = R.string.block_screen_first_core_action_feedback),
                        textAlign = TextAlign.Center,
                        color = KeepTheme.colors.onPrimaryContainer,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 시스템 바 여백은 이 화면을 감싼 Scaffold 가 이미 넣어 준다. 여기서 또 주면
                // 두 번 들어가 버튼 아래가 손가락 두 마디만큼 비어 버린다.
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            uiState.repeatBlockRoutineSuggestion?.let { suggestion ->
                RepeatBlockRoutineSuggestionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("block_screen_repeat_block_suggestion_card"),
                    suggestion = suggestion,
                    titleResId = R.string.repeat_block_suggestion_post_block_success_title,
                    messageResId = R.string.repeat_block_suggestion_post_block_success_message,
                    onApplyClick = onOpenRoutineSuggestion,
                    onDismissClick = onDismissRoutineSuggestion,
                    applyActionTestTag = "block_screen_repeat_block_suggestion_apply_action",
                    dismissActionTestTag = "block_screen_repeat_block_suggestion_dismiss_action",
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            val emergencyUnlockAction = emergencyUnlockActionUiState(uiState.emergencyUnlockAvailabilityReason)
            KeepTextButton(
                modifier = Modifier.testTag("block_screen_emergency_unlock_action"),
                onClick = onShowEmergencyUnlock,
                enabled = emergencyUnlockAction.enabled,
            ) {
                Text(
                    text = if (emergencyUnlockAction.enabled) {
                        stringResource(
                            emergencyUnlockAction.textRes,
                            uiState.dailyUnlockRemaining,
                            uiState.emergencyUnlockDailyLimit,
                        )
                    } else {
                        stringResource(emergencyUnlockAction.textRes)
                    },
                    color = if (emergencyUnlockAction.enabled) {
                        KeepTheme.colors.primary
                    } else {
                        KeepTheme.colors.surfaceVariant
                    },
                    fontSize = 13.sp,
                )
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("block_screen_emergency_unlock_helper"),
                text = stringResource(emergencyUnlockAction.helperTextRes),
                textAlign = TextAlign.Center,
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            KeepButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("block_screen_close_cta"),
                text = stringResource(id = R.string.block_screen_close),
                onClick = onClose,
            )
        }
    }
}
