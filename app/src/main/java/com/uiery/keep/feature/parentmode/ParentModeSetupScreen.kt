package com.uiery.keep.feature.parentmode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.uiery.kds.KeepIconButton
import com.uiery.kds.KeepLinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.uiery.kds.KeepTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.keep.ui.component.BottomActionBar
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonVariant
import com.uiery.kds.KeepBadge
import com.uiery.kds.KeepBadgeTone
import com.uiery.kds.KeepBadgeVariant
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.parentmode.ParentModeSession
import com.uiery.keep.domain.parentmode.ParentModeSessionState
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.SetupAppRow
import com.uiery.keep.ui.component.SetupChip
import com.uiery.keep.ui.component.SetupGroupCard
import com.uiery.keep.ui.component.SetupHero
import com.uiery.keep.ui.component.SetupSecondaryButton
import com.uiery.keep.ui.component.SetupSectionCaption
import com.uiery.keep.ui.component.SetupSectionHeader
import com.uiery.keep.ui.component.SetupTextField
import kotlinx.coroutines.delay

private val PARENT_MODE_DURATION_OPTIONS = listOf(10, 20, 30, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ParentModeSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParentModeSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appSelectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isAppSelectionSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.loadAllowedAppsFromCurrentSelection()
    }

    if (isAppSelectionSheetVisible) {
        KeepModalBottomSheet(
            sheetState = appSelectionSheetState,
            onDismissRequest = { isAppSelectionSheetVisible = false },
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = state.allowedApps,
                onComplete = { selectedApps ->
                    viewModel.setAllowedApps(selectedApps)
                    isAppSelectionSheetVisible = false
                },
            )
        }
    }

    val pinMismatch = state.guardianPinConfirmation.isNotEmpty() &&
        state.pinState == ParentModePinState.Failed

    Scaffold(
        topBar = {
            KeepTopAppBar(
                navigationIcon = {
                    KeepIconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(id = R.string.parent_mode_setup_title),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        val activeSession = state.activeSession
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (activeSession == null) {
                ParentModeSetupForm(
                    state = state,
                    pinMismatch = pinMismatch,
                    onDurationSelected = viewModel::setDurationMinutes,
                    onCustomDurationChanged = viewModel::updateCustomDurationInput,
                    onReloadCurrentSelection = viewModel::loadAllowedAppsFromCurrentSelection,
                    onAdjustApps = { isAppSelectionSheetVisible = true },
                    onGuardianPinChanged = viewModel::updateGuardianPin,
                    onGuardianPinConfirmationChanged = viewModel::updateGuardianPinConfirmation,
                )
            } else {
                ParentModeActiveControls(
                    state = state,
                    session = activeSession,
                    pinMismatch = pinMismatch,
                    onGuardianPinChanged = viewModel::updateGuardianPin,
                    onGuardianPinConfirmationChanged = viewModel::updateGuardianPinConfirmation,
                    onRefresh = viewModel::refreshActiveSessionStatus,
                    onExtend = viewModel::extendActiveSessionByTenMinutes,
                    onEnd = viewModel::endActiveSessionFromSetupInput,
                    onPrepareAnother = viewModel::prepareAnotherParentModeSession,
                )
            }
        }
            if (activeSession == null) {
                BottomActionBar {
                    KeepButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.parent_mode_setup_start),
                        enabled = state.canAttemptStart,
                        onClick = viewModel::startParentModeFromSetupInput,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentModeSetupForm(
    state: ParentModeSetupUiState,
    pinMismatch: Boolean,
    onDurationSelected: (Int) -> Unit,
    onCustomDurationChanged: (String) -> Unit,
    onReloadCurrentSelection: () -> Unit,
    onAdjustApps: () -> Unit,
    onGuardianPinChanged: (String) -> Unit,
    onGuardianPinConfirmationChanged: (String) -> Unit,
) {
    val setupAccessibilitySummary = stringResource(
        id = R.string.parent_mode_setup_accessibility_summary,
        state.durationMinutes,
        state.allowedApps.size,
    )

    SetupHero(
        iconResId = R.drawable.ic_parent_mode,
        title = stringResource(id = R.string.parent_mode_setup_headline),
        subtitle = stringResource(id = R.string.parent_mode_setup_description),
    )

    SetupGroupCard(
        modifier = Modifier.semantics { contentDescription = setupAccessibilitySummary },
    ) {
        SetupSectionHeader(
            title = stringResource(id = R.string.parent_mode_setup_duration_label),
            valueLabel = stringResource(
                id = R.string.parent_mode_setup_duration_minutes,
                state.durationMinutes,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PARENT_MODE_DURATION_OPTIONS.forEach { minutes ->
                SetupChip(
                    label = stringResource(
                        id = R.string.parent_mode_setup_duration_minutes,
                        minutes,
                    ),
                    selected = state.durationMinutes == minutes,
                    onClick = { onDurationSelected(minutes) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SetupTextField(
            value = state.customDurationInput,
            onValueChange = onCustomDurationChanged,
            placeholder = stringResource(id = R.string.parent_mode_setup_duration_custom_label),
            keyboardType = KeyboardType.Number,
            isError = state.durationMinutes <= 0,
        )
        Spacer(modifier = Modifier.height(6.dp))
        SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_duration_custom_helper))
    }

    SetupGroupCard {
        SetupSectionHeader(
            title = stringResource(id = R.string.parent_mode_setup_allowed_apps_label),
            valueLabel = state.allowedApps.size.toString(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        SetupSectionCaption(
            text = stringResource(
                id = R.string.parent_mode_setup_allowed_apps_summary,
                state.allowedApps.size,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupSecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(id = R.string.parent_mode_setup_reload_current_selection),
                onClick = onReloadCurrentSelection,
            )
            SetupSecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(id = R.string.parent_mode_setup_adjust_apps),
                onClick = onAdjustApps,
            )
        }
        if (state.allowedApps.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_apps_empty))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.allowedApps.sorted().forEach { packageName ->
                    SetupAppRow(
                        packageName = packageName,
                        fallbackLabel = packageName,
                    )
                }
            }
        }
    }

    SetupGroupCard {
        SetupSectionHeader(title = stringResource(id = R.string.parent_mode_setup_pin_label))
        Spacer(modifier = Modifier.height(6.dp))
        SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_pin_required_notice))
        Spacer(modifier = Modifier.height(12.dp))
        SetupTextField(
            value = state.guardianPin,
            onValueChange = onGuardianPinChanged,
            placeholder = stringResource(id = R.string.parent_mode_setup_pin_label),
            keyboardType = KeyboardType.NumberPassword,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_pin_helper))
        Spacer(modifier = Modifier.height(12.dp))
        SetupTextField(
            value = state.guardianPinConfirmation,
            onValueChange = onGuardianPinConfirmationChanged,
            placeholder = stringResource(id = R.string.parent_mode_setup_pin_confirm_label),
            keyboardType = KeyboardType.NumberPassword,
            visualTransformation = PasswordVisualTransformation(),
            isError = pinMismatch,
        )
        if (pinMismatch) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.parent_mode_setup_pin_mismatch),
                color = KeepTheme.colors.error,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }

}

@Composable
internal fun ParentModeActiveControls(
    state: ParentModeSetupUiState,
    session: ParentModeSession,
    pinMismatch: Boolean,
    onGuardianPinChanged: (String) -> Unit,
    onGuardianPinConfirmationChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onExtend: () -> Unit,
    onEnd: () -> Unit,
    onPrepareAnother: () -> Unit,
) {
    LaunchedEffect(session.expiresAtMillis, session.state) {
        onRefresh()
        activeSessionRefreshDelayMillis(session, nowMillis = System.currentTimeMillis())?.let { delayMillis ->
            delay(delayMillis)
            onRefresh()
        }
    }

    var nowMillis by remember(session.startedAtMillis, session.expiresAtMillis) {
        mutableStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(session.expiresAtMillis, session.state) {
        if (session.state == ParentModeSessionState.Active) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                if (nowMillis >= session.expiresAtMillis) break
                delay(1_000L)
            }
        }
    }
    val remainingMillis = (session.expiresAtMillis - nowMillis).coerceAtLeast(0L)

    val statusTextRes = when (session.state) {
        ParentModeSessionState.Active -> R.string.parent_mode_active_title
        ParentModeSessionState.Expired -> R.string.parent_mode_expired_title
        ParentModeSessionState.UnlockedByPin -> R.string.parent_mode_ended_title
        ParentModeSessionState.Setup,
        ParentModeSessionState.Cancelled,
        -> R.string.parent_mode_setup_title
    }
    val statusText = stringResource(id = statusTextRes)
    val activeAccessibilitySummary = stringResource(
        id = R.string.parent_mode_active_accessibility_summary,
        statusText,
        session.durationMinutes,
        session.allowedApps.size,
    )
    val canUseGuardianAction = session.state == ParentModeSessionState.Active &&
        state.pinState == ParentModePinState.Verified

    Column(
        modifier = Modifier.semantics { contentDescription = activeAccessibilitySummary },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ParentModeStatusHeroCard(
            session = session,
            statusText = statusText,
            remainingMillis = remainingMillis,
        )

        SetupGroupCard {
            SetupSectionHeader(title = stringResource(id = R.string.parent_mode_active_guardian_title))
            Spacer(modifier = Modifier.height(8.dp))
            SetupSectionCaption(text = stringResource(id = R.string.parent_mode_active_pin_notice))
            Spacer(modifier = Modifier.height(14.dp))
            SetupTextField(
                value = state.guardianPin,
                onValueChange = onGuardianPinChanged,
                placeholder = stringResource(id = R.string.parent_mode_setup_pin_label),
                keyboardType = KeyboardType.NumberPassword,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupTextField(
                value = state.guardianPinConfirmation,
                onValueChange = onGuardianPinConfirmationChanged,
                placeholder = stringResource(id = R.string.parent_mode_setup_pin_confirm_label),
                keyboardType = KeyboardType.NumberPassword,
                visualTransformation = PasswordVisualTransformation(),
                isError = pinMismatch,
            )
            if (pinMismatch || ParentModeSetupIssue.PinNotVerified in state.setupIssues) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.parent_mode_setup_pin_mismatch),
                    color = KeepTheme.colors.error,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.parent_mode_active_extend_ten_minutes),
                enabled = canUseGuardianAction,
                bottomSpacing = false,
                onClick = onExtend,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.parent_mode_active_end_now),
                enabled = canUseGuardianAction,
                variant = KeepButtonVariant.CriticalSolid,
                bottomSpacing = false,
                onClick = onEnd,
            )
        }

        if (session.state != ParentModeSessionState.Active) {
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.parent_mode_prepare_another_session),
                bottomSpacing = false,
                onClick = onPrepareAnother,
            )
        }
    }
}

@Composable
private fun ParentModeStatusHeroCard(
    session: ParentModeSession,
    statusText: String,
    remainingMillis: Long,
) {
    val isActive = session.state == ParentModeSessionState.Active
    val accent = when (session.state) {
        ParentModeSessionState.Active -> KeepTheme.colors.primary
        ParentModeSessionState.Expired -> KeepTheme.colors.error
        else -> KeepTheme.colors.onTertiaryContainer
    }
    val badgeText = stringResource(
        id = when (session.state) {
            ParentModeSessionState.Active -> R.string.parent_mode_status_active
            ParentModeSessionState.Expired -> R.string.parent_mode_status_expired
            else -> R.string.parent_mode_status_ended
        },
    )
    val totalMillis = (session.expiresAtMillis - session.startedAtMillis).coerceAtLeast(1L)
    val fraction = (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "parent_mode_progress")

    KeepCard(
        modifier = Modifier
            .fillMaxWidth(),
        variant = when (session.state) {
            ParentModeSessionState.Active -> KeepCardVariant.BrandWeak
            ParentModeSessionState.Expired -> KeepCardVariant.CriticalWeak
            else -> KeepCardVariant.NeutralWeak
        },
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            KeepBadge(
                text = badgeText,
                tone = when (session.state) {
                    ParentModeSessionState.Active -> KeepBadgeTone.Brand
                    ParentModeSessionState.Expired -> KeepBadgeTone.Critical
                    else -> KeepBadgeTone.Neutral
                },
                variant = KeepBadgeVariant.Weak,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isActive) {
                Text(
                    text = stringResource(id = R.string.parent_mode_active_remaining_label),
                    color = KeepTheme.colors.onTertiaryContainer,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatParentModeRemaining(remainingMillis),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(modifier = Modifier.height(14.dp))
                KeepLinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                )
            } else {
                Text(
                    text = statusText,
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(
                    id = R.string.parent_mode_active_summary,
                    session.durationMinutes,
                    session.allowedApps.size,
                ),
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 13.sp,
            )
        }
    }
}

private fun formatParentModeRemaining(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

internal fun activeSessionRefreshDelayMillis(
    session: ParentModeSession,
    nowMillis: Long,
): Long? {
    if (session.state != ParentModeSessionState.Active) return null
    return (session.expiresAtMillis - nowMillis).coerceAtLeast(0L)
}
