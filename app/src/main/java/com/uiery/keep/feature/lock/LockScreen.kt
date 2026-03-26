package com.uiery.keep.feature.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.KeepBannerAd
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.RotatingCircleGradient
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.CategoryButton
import com.uiery.keep.feature.lock.component.CountDownContent
import com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContent
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
    val uiState by viewModel.collectAsState()
    val configuration = LocalConfiguration.current

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is LockSideEffect.MoveToHome -> onNavigateHome()
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            CategoryButton(
                modifier = Modifier.padding(top = 60.dp),
                onClick = { },
                enabled = false,
                categorySize = uiState.selectedAppPackage.size,
            )
            if (uiState.isEmergencyUnlockActive) {
                val minutes = uiState.emergencyUnlockRemainingSeconds / 60
                val seconds = uiState.emergencyUnlockRemainingSeconds % 60
                val timeText = String.format("%d:%02d", minutes, seconds)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(
                            color = KeepTheme.colors.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
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
                        val name = uiState.routines.firstOrNull()?.name.orEmpty()
                        Text(
                            text = stringResource(
                                id = R.string.lock_screen_routine_running,
                                name,
                            ),
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
                RotatingCircleGradient(
                    size = configuration.screenWidthDp.dp - 80.dp,
                )
            }
            Column(
                modifier = modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val lockTime = uiState.lockTime
                if (!uiState.isEmergencyUnlockActive) {
                    TextButton(
                        onClick = viewModel::showEmergencyUnlockSheet,
                        enabled = !uiState.dailyLimitReached,
                    ) {
                        Text(
                            text = if (uiState.dailyLimitReached) {
                                stringResource(R.string.emergency_unlock_daily_limit_reached)
                            } else {
                                stringResource(R.string.emergency_unlock)
                            },
                            color = if (uiState.dailyLimitReached) {
                                KeepTheme.colors.surfaceVariant
                            } else {
                                KeepTheme.colors.primary
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (!uiState.dailyLimitReached) {
                        Text(
                            text = stringResource(R.string.emergency_unlock_remaining_count, uiState.dailyUnlockRemaining),
                            fontSize = 12.sp,
                            color = KeepTheme.colors.surfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(id = R.string.keep_on_status),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                val isMultiDay = lockTime.toLocalDate() != java.time.LocalDate.now()
                Text(
                    text = if (isMultiDay) {
                        stringResource(
                            id = R.string.lock_screen_unavailable_message_with_date,
                            lockTime.monthValue,
                            lockTime.dayOfMonth,
                            lockTime.hour,
                            lockTime.minute,
                        )
                    } else {
                        stringResource(
                            id = R.string.lock_screen_unavailable_message,
                            lockTime.hour,
                            lockTime.minute,
                        )
                    },
                    color = KeepTheme.colors.surfaceVariant,
                )
                KeepBannerAd(
                    modifier = Modifier.padding(top = 20.dp),
                    adUnitId = "ca-app-pub-1537867411423705/7892727021"
                )
            }
        }
    }
}