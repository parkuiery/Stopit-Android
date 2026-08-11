package com.uiery.keep.feature.lock

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.uiery.kds.KeepTextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.RotatingCircleGradient
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.toMetadata
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.domain.lock.LockTargetKind
import com.uiery.keep.ui.component.CategoryButton
import com.uiery.keep.ui.component.WebsiteBlockingUnavailableBanner
import com.uiery.keep.ui.component.CountDownContent
import com.uiery.keep.ui.component.EmergencyUnlockBottomSheetContent
import com.uiery.keep.service.emergencyUnlockActionUiState
import com.uiery.keep.util.formatLockEndTime
import com.uiery.keep.util.formatMinuteSecondCountdown
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
) {
    BackHandler(enabled = true) {
        // Active locks must not be dismissed by system back; only explicit policy
        // exits such as timer completion or emergency unlock may navigate home.
    }

    val uiState by viewModel.collectAsState()
    val configuration = LocalConfiguration.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is LockSideEffect.MoveToHome,
            is LockSideEffect.UnlockCompleted,
            -> onNavigateHome()
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val emergencyUnlockSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.isShowEmergencyUnlockSheet) {
        KeepModalBottomSheet(
            sheetState = emergencyUnlockSheetState,
            onDismissRequest = viewModel::hideEmergencyUnlockSheet,
        ) {
            EmergencyUnlockBottomSheetContent(
                blockedApps = uiState.selectedAppPackage,
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

    LockScreenLayout(
        modifier = modifier.fillMaxSize(),
        banner = {
            TrackedBannerAd(
                modifier = Modifier.fillMaxWidth(),
                metadata = AdPlacement.LockBottom.toMetadata(
                    screenName = KeepAnalyticsScreen.LOCK,
                    screenContext = if (uiState.isRoutine) "routine" else "manual",
                ),
            )
        },
    ) {
            CategoryButton(
                modifier = Modifier.padding(top = 60.dp),
                onClick = { },
                enabled = false,
                categorySize = uiState.selectedAppPackage.size,
                websiteSize = uiState.selectedWebDomains.size,
            )
            AnimatedVisibility(
                visible = uiState.isEmergencyUnlockActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val timeText = formatMinuteSecondCountdown(uiState.emergencyUnlockRemainingSeconds)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(
                            color = KeepTheme.colors.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(KeepTheme.colors.primary, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            R.string.emergency_unlock_banner,
                            uiState.emergencyUnlockedApps.size,
                            timeText,
                        ),
                        color = KeepTheme.colors.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
            WebsiteBlockingUnavailableBanner(
                hasWebsiteTargets = uiState.selectedWebDomains.isNotEmpty(),
                onConsentGranted = viewModel::retryWebsiteBlocking,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                RotatingCircleGradient(
                    size = configuration.screenWidthDp.dp - 80.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    Image(
                        modifier = Modifier
                            .sizeIn(
                                minHeight = 100.dp,
                                minWidth = 100.dp,
                            )
                            .clip(RoundedCornerShape(12.dp)),
                        painter = painterResource(id = R.drawable.kepp_icon),
                        contentDescription = null,
                    )
                    if (uiState.isRoutine) {
                        val routineText =
                            if (uiState.routines.size == 1) {
                                stringResource(
                                    id = R.string.lock_screen_routine_running,
                                    uiState.routines.first().name,
                                )
                            } else {
                                stringResource(
                                    id = R.string.lock_screen_routines_running,
                                    uiState.routines.size,
                                )
                            }
                        Text(
                            text = routineText,
                            fontWeight = FontWeight.Medium,
                            color = KeepTheme.colors.surfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        CountDownContent(
                            endTime = uiState.lockTime
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val lockTime = uiState.lockTime
                val isMultiDay = lockTime.toLocalDate() != java.time.LocalDate.now()
                val formattedTime = formatLockEndTime(lockTime)
                Text(
                    text = stringResource(id = R.string.keep_on_status),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        lockUnavailableMessageRes(
                            lockTargetKind = LockTargetKind.of(
                                hasApps = uiState.selectedAppPackage.isNotEmpty(),
                                hasWebsites = uiState.selectedWebDomains.isNotEmpty(),
                            ),
                            isMultiDay = isMultiDay,
                        ),
                        formattedTime,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    color = KeepTheme.colors.surfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!uiState.isEmergencyUnlockActive) {
                    val emergencyUnlockAction = emergencyUnlockActionUiState(uiState.emergencyUnlockAvailabilityReason)
                    KeepTextButton(
                        onClick = viewModel::showEmergencyUnlockSheet,
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
                }
            }
    }
}

@StringRes
internal fun lockUnavailableMessageRes(
    lockTargetKind: LockTargetKind,
    isMultiDay: Boolean,
): Int = when (lockTargetKind) {
    LockTargetKind.Apps -> if (isMultiDay) {
        R.string.lock_screen_unavailable_message_with_date
    } else {
        R.string.lock_screen_unavailable_message
    }
    LockTargetKind.Websites -> if (isMultiDay) {
        R.string.lock_screen_unavailable_message_websites_with_date
    } else {
        R.string.lock_screen_unavailable_message_websites
    }
    LockTargetKind.AppsAndWebsites -> if (isMultiDay) {
        R.string.lock_screen_unavailable_message_apps_and_websites_with_date
    } else {
        R.string.lock_screen_unavailable_message_apps_and_websites
    }
}

@Composable
internal fun LockScreenLayout(
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                banner()
            }
        }
    }
}
