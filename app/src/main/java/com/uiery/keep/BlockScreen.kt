package com.uiery.keep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.uiery.keep.service.emergencyUnlockActionUiState
import com.uiery.keep.ui.component.CountDownContent
import com.uiery.keep.ui.component.EmergencyUnlockBottomSheetContent
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
) {
    val appDisplayMetadataResolver = rememberAppDisplayMetadataResolver()
    val uiState by viewModel.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val emergencyUnlockSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appName = remember(packageName, appDisplayMetadataResolver) {
        appDisplayMetadataResolver.resolve(packageName).label
    }

    LaunchedEffect(packageName, blockSource, routineId, goalLockId) {
        viewModel.syncManualTimedLockReentry(blockSource)
        viewModel.trackBlockShown(packageName, blockSource, routineId, goalLockId)
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
        uiState = uiState,
        onShowEmergencyUnlock = viewModel::showEmergencyUnlockSheet,
        onClose = onClose,
    )
}

@Composable
internal fun BlockScreenContent(
    appName: String,
    uiState: BlockUiState,
    onShowEmergencyUnlock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showBannerAd: Boolean = true,
) {
    BackHandler(enabled = true) {
        // Keep the protection surface in front. The explicit close CTA remains the
        // allowed way to leave the blocked app by sending the user Home.
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KeepTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (showBannerAd) {
            TrackedBannerAd(
                modifier = Modifier.align(Alignment.TopCenter),
                metadata = AdPlacement.BlockTop.toMetadata(
                    screenName = KeepAnalyticsScreen.BLOCK,
                    screenContext = "blocked_app",
                ),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_title),
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp,
                    textAlign = TextAlign.Center,
                    fontSize = 32.sp,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 20.dp))
                Text(
                    text = stringResource(id = R.string.block_screen_message, appName),
                    textAlign = TextAlign.Center,
                    color = KeepTheme.colors.surfaceVariant,
                )
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val emergencyUnlockAction = emergencyUnlockActionUiState(uiState.emergencyUnlockAvailabilityReason)
                TextButton(
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
}
