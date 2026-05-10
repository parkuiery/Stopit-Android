package com.uiery.keep.feature.emergencyunlocksettings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.KeepSwitch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmergencyUnlockSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EmergencyUnlockSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KeepTheme.colors.background),
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
private fun HeroIntro(
    enabled: Boolean,
    dailyLimit: Int,
) {
    val accent = KeepTheme.colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
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
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            if (enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KeepTheme.colors.background)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.emergency_unlock_settings_limit_count,
                            dailyLimit,
                        ) + " / " + stringResource(R.string.emergency_unlock_settings_daily_limit),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KeepTheme.colors.onSecondary)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .animateContentSize(),
        content = content,
    )
}

@Composable
private fun GroupDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(1.dp)
            .background(KeepTheme.colors.tertiary.copy(alpha = 0.4f)),
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
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = valueLabel,
            color = KeepTheme.colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        KeepSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
                    color = if (isSelected) Color.White else KeepTheme.colors.onSurfaceVariant,
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
            DurationChip(
                label = stringResource(R.string.emergency_unlock_duration_minutes, minutes),
                selected = isSelected,
                enabled = enabled && !isLastSelected,
                onClick = { onToggle(minutes) },
            )
        }
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = KeepTheme.colors.primary
    val containerColor = if (selected) primary.copy(alpha = 0.12f) else KeepTheme.colors.background
    val borderColor = if (selected) primary else KeepTheme.colors.tertiary.copy(alpha = 0.6f)
    val textColor = if (selected) primary else KeepTheme.colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private fun Modifier.dimWhen(condition: Boolean): Modifier =
    if (condition) this.alpha(0.45f) else this
