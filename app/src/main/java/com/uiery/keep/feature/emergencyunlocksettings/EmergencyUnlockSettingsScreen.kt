package com.uiery.keep.feature.emergencyunlocksettings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepChip
import com.uiery.kds.KeepChipRole
import com.uiery.kds.KeepChipVariant
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.KeepBadge
import com.uiery.kds.KeepBadgeTone
import com.uiery.kds.KeepBadgeVariant
import com.uiery.kds.KeepDivider
import com.uiery.kds.KeepLabel
import com.uiery.kds.KeepLabelSize
import com.uiery.kds.KeepLabelTone
import com.uiery.kds.KeepLabelWeight
import com.uiery.kds.KeepSelectableCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.uiery.kds.KeepTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.kds.KeepSwitch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmergencyUnlockSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EmergencyUnlockSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showManualRefillModeDialog by remember { mutableStateOf(false) }
    var showManualResetDialog by remember { mutableStateOf(false) }

    if (showManualRefillModeDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.emergency_unlock_auto_reset_disable_dialog_title),
            message = stringResource(R.string.emergency_unlock_auto_reset_disable_dialog_message),
            confirmLabel = stringResource(R.string.emergency_unlock_auto_reset_disable_dialog_confirm),
            dismissLabel = stringResource(R.string.emergency_unlock_cancel),
            onDismiss = { showManualRefillModeDialog = false },
            onConfirm = {
                showManualRefillModeDialog = false
                viewModel.setRefillMode(EmergencyUnlockRefillMode.Manual)
            },
        )
    }

    if (showManualResetDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.emergency_unlock_manual_reset_dialog_title),
            message = stringResource(R.string.emergency_unlock_manual_reset_dialog_message),
            confirmLabel = stringResource(R.string.emergency_unlock_manual_reset_dialog_confirm),
            dismissLabel = stringResource(R.string.emergency_unlock_cancel),
            onDismiss = { showManualResetDialog = false },
            onConfirm = {
                showManualResetDialog = false
                viewModel.markManualReset()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            KeepTopAppBar(
                navigationIcon = {
                    KeepIconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.emergency_unlock_settings_title),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroIntro(
                enabled = uiState.enabled,
                dailyLimit = uiState.dailyLimit,
            )

            MasterToggleCard(
                checked = uiState.enabled,
                onCheckedChange = viewModel::setEnabled,
            )

            SettingsGroupCard(modifier = Modifier.dimWhen(!uiState.enabled)) {
                SectionHeader(
                    title = stringResource(R.string.emergency_unlock_settings_daily_limit),
                    valueLabel = stringResource(
                        R.string.emergency_unlock_settings_limit_count,
                        uiState.dailyLimit,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                DailyLimitSelector(
                    options = uiState.allowedDailyLimits.filter { it > 0 },
                    selected = uiState.dailyLimit,
                    enabled = uiState.enabled,
                    onSelected = viewModel::setDailyLimit,
                )

                GroupDivider()

                RefillModeSection(
                    uiState = uiState,
                    onDailySelected = { viewModel.setRefillMode(EmergencyUnlockRefillMode.Daily) },
                    onManualSelected = { showManualRefillModeDialog = true },
                    onManualResetClick = { showManualResetDialog = true },
                )

                GroupDivider()

                val durationValueLabel = uiState.durationOptions
                    .sorted()
                    .map { stringResource(R.string.emergency_unlock_duration_minutes, it) }
                    .joinToString(separator = " · ")
                SectionHeader(
                    title = stringResource(R.string.emergency_unlock_settings_durations),
                    valueLabel = durationValueLabel,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DurationSelector(
                    options = uiState.allowedDurations,
                    selected = uiState.durationOptions,
                    enabled = uiState.enabled,
                    onToggle = viewModel::toggleDuration,
                )

                GroupDivider()

                SwitchRow(
                    title = stringResource(R.string.emergency_unlock_settings_countdown),
                    subtitle = stringResource(R.string.emergency_unlock_settings_countdown_subtitle),
                    checked = uiState.countdownEnabled,
                    enabled = uiState.enabled,
                    onCheckedChange = viewModel::setCountdownEnabled,
                )
                if (uiState.countdownEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CountdownSecondsSelector(
                        options = uiState.allowedCountdownSeconds,
                        selected = uiState.countdownSeconds,
                        enabled = uiState.enabled,
                        onSelected = viewModel::setCountdownSeconds,
                    )
                }
            }

            SettingsGroupCard(modifier = Modifier.dimWhen(!uiState.enabled)) {
                SwitchRow(
                    title = stringResource(R.string.emergency_unlock_settings_reason_required),
                    subtitle = stringResource(R.string.emergency_unlock_settings_reason_required_subtitle),
                    checked = uiState.reasonRequired,
                    enabled = uiState.enabled,
                    onCheckedChange = viewModel::setReasonRequired,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    KeepConfirmationDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun HeroIntro(
    enabled: Boolean,
    dailyLimit: Int,
) {
    val accent = KeepTheme.colors.primary
    KeepCard(
        modifier = Modifier
            .fillMaxWidth(),
        variant = KeepCardVariant.BrandWeak,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (enabled) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = R.drawable.ic_emergency),
                contentDescription = null,
                tint = accent,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.emergency_unlock_settings_enabled),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) {
                    stringResource(R.string.emergency_unlock_settings_enabled_subtitle)
                } else {
                    stringResource(R.string.emergency_unlock_settings_enabled_subtitle)
                },
                color = KeepTheme.semanticColors.foreground.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            if (enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                KeepBadge(
                    text = stringResource(
                        R.string.emergency_unlock_settings_limit_count,
                        dailyLimit,
                    ) + " / " + stringResource(R.string.emergency_unlock_settings_daily_limit),
                    tone = KeepBadgeTone.Brand,
                    variant = KeepBadgeVariant.Outline,
                )
            }
        }
        }
    }
}

@Composable
private fun MasterToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsGroupCard {
        SwitchRow(
            title = stringResource(R.string.emergency_unlock_settings_enabled),
            subtitle = stringResource(R.string.emergency_unlock_settings_enabled_subtitle),
            checked = checked,
            enabled = true,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KeepCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun GroupDivider() {
    KeepDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    valueLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeepLabel(
            modifier = Modifier.weight(1f),
            text = title,
            size = KeepLabelSize.Large,
            weight = KeepLabelWeight.Strong,
        )
        KeepLabel(
            text = valueLabel,
            tone = KeepLabelTone.Brand,
            size = KeepLabelSize.Medium,
            weight = KeepLabelWeight.Strong,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    contentDescription: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = KeepTheme.semanticColors.foreground.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        KeepSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RefillModeSection(
    uiState: EmergencyUnlockSettingsUiState,
    onDailySelected: () -> Unit,
    onManualSelected: () -> Unit,
    onManualResetClick: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.emergency_unlock_settings_count_management),
        valueLabel = stringResource(
            R.string.emergency_unlock_settings_remaining_count,
            uiState.remainingUnlockCount,
            uiState.dailyLimit,
        ),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.emergency_unlock_settings_count_management_subtitle),
        color = KeepTheme.semanticColors.foreground.muted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    Spacer(modifier = Modifier.height(12.dp))
    RefillModeOption(
        title = stringResource(R.string.emergency_unlock_settings_daily_refill_title),
        subtitle = stringResource(R.string.emergency_unlock_settings_daily_refill_subtitle),
        badge = null,
        selected = uiState.refillMode == EmergencyUnlockRefillMode.Daily,
        enabled = uiState.enabled,
        contentDescription = stringResource(R.string.cd_emergency_unlock_daily_refill_mode),
        onClick = onDailySelected,
    )
    Spacer(modifier = Modifier.height(10.dp))
    RefillModeOption(
        title = stringResource(R.string.emergency_unlock_settings_manual_refill_title),
        subtitle = stringResource(R.string.emergency_unlock_settings_manual_refill_subtitle),
        badge = if (uiState.refillMode == EmergencyUnlockRefillMode.Manual) {
            stringResource(
                R.string.emergency_unlock_settings_remaining_count,
                uiState.remainingUnlockCount,
                uiState.dailyLimit,
            )
        } else {
            null
        },
        selected = uiState.refillMode == EmergencyUnlockRefillMode.Manual,
        enabled = uiState.enabled,
        contentDescription = stringResource(R.string.cd_emergency_unlock_manual_refill_mode),
        onClick = onManualSelected,
    )
    if (uiState.refillMode == EmergencyUnlockRefillMode.Manual) {
        Spacer(modifier = Modifier.height(12.dp))
        ManualResetButton(
            enabled = uiState.enabled,
            contentDescription = stringResource(R.string.cd_emergency_unlock_manual_reset_button),
            onClick = onManualResetClick,
        )
    }
}

@Composable
private fun RefillModeOption(
    title: String,
    subtitle: String,
    badge: String?,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    KeepSelectableCard(
        title = title,
        description = subtitle,
        selected = selected,
        enabled = enabled,
        contentDescription = contentDescription,
        onClick = onClick,
        supportingContent = if (badge != null) {
            {
                KeepBadge(
                    text = badge,
                    tone = KeepBadgeTone.Brand,
                    variant = KeepBadgeVariant.Weak,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun ManualResetButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.background)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.emergency_unlock_settings_manual_reset_button),
            color = KeepTheme.colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DailyLimitSelector(
    options: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.background)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { value ->
            val isSelected = value == selected
            val bg by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                label = "limit_bg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KeepTheme.colors.primary.copy(alpha = bg * 0.95f))
                    .clickable(enabled = enabled) { onSelected(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    color = if (isSelected) {
                        KeepTheme.semanticColors.foreground.onBrand
                    } else {
                        KeepTheme.colors.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun CountdownSecondsSelector(
    options: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.background)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { seconds ->
            val isSelected = seconds == selected
            val bg by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                label = "countdown_bg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KeepTheme.colors.primary.copy(alpha = bg * 0.95f))
                    .clickable(enabled = enabled) { onSelected(seconds) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.emergency_unlock_countdown_seconds, seconds),
                    color = if (isSelected) {
                        KeepTheme.semanticColors.foreground.onBrand
                    } else {
                        KeepTheme.colors.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSelector(
    options: List<Int>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { minutes ->
            val isSelected = minutes in selected
            val isLastSelected = isSelected && selected.size == 1
            KeepChip(
                text = stringResource(R.string.emergency_unlock_duration_minutes, minutes),
                selected = isSelected,
                enabled = enabled && !isLastSelected,
                variant = KeepChipVariant.OutlineStrong,
                role = KeepChipRole.Toggle,
                onClick = { onToggle(minutes) },
            )
        }
    }
}

private fun Modifier.dimWhen(condition: Boolean): Modifier =
    if (condition) this.alpha(0.45f) else this
