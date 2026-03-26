package com.uiery.keep

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.KeepBannerAd
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.feature.lock.component.EmergencyUnlockBottomSheetContent
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockScreen(
    modifier: Modifier = Modifier,
    packageName: String,
    viewModel: BlockViewModel = hiltViewModel(),
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val uiState by viewModel.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val emergencyUnlockSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is BlockSideEffect.UnlockCompleted -> onClose()
        }
    }

    if (uiState.isShowEmergencyUnlockSheet) {
        KeepModalBottomSheet(
            sheetState = emergencyUnlockSheetState,
            onDismissRequest = viewModel::hideEmergencyUnlockSheet,
        ) {
            EmergencyUnlockBottomSheetContent(
                blockedApps = setOf(packageName),
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KeepTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        KeepBannerAd(
            modifier = Modifier.align(Alignment.TopCenter),
            adUnitId = "ca-app-pub-1537867411423705/5467753282"
        )
        Column {
            val appName = runCatching {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            }.getOrDefault("")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
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
                    contentDescription = null
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
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!uiState.dailyLimitReached) {
                    TextButton(
                        onClick = viewModel::showEmergencyUnlockSheet,
                    ) {
                        Text(
                            text = stringResource(R.string.emergency_unlock),
                            color = KeepTheme.colors.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.emergency_unlock_remaining_count, uiState.dailyUnlockRemaining),
                        fontSize = 12.sp,
                        color = KeepTheme.colors.surfaceVariant,
                    )
                }
                KeepButton(
                    modifier = Modifier
                        .padding(horizontal = 20.dp),
                    text = stringResource(id = R.string.block_screen_close),
                    onClick = onClose,
                )
            }
        }
    }
}
