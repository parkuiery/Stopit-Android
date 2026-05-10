package com.uiery.keep.feature.emergencyunlocksettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
                            tint = Color(0xFFFE9E0B),
                        )
                    }
                },
                title = { Text(text = stringResource(R.string.emergency_unlock_settings_title)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.emergency_unlock_settings_enabled),
                subtitle = stringResource(R.string.emergency_unlock_settings_enabled_subtitle),
                checked = uiState.enabled,
                onCheckedChange = viewModel::setEnabled,
            )
            SettingSectionTitle(text = stringResource(R.string.emergency_unlock_settings_daily_limit))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.allowedDailyLimits.forEach { limit ->
                    FilterChip(
                        selected = uiState.dailyLimit == limit,
                        onClick = { viewModel.setDailyLimit(limit) },
                        label = {
                            Text(text = stringResource(R.string.emergency_unlock_settings_limit_count, limit))
                        },
                    )
                }
            }
            SettingSectionTitle(text = stringResource(R.string.emergency_unlock_settings_durations))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.allowedDurations.forEach { minutes ->
                    DurationOptionRow(
                        minutes = minutes,
                        checked = minutes in uiState.durationOptions,
                        onClick = { viewModel.toggleDuration(minutes) },
                    )
                }
            }
            SettingSwitchRow(
                title = stringResource(R.string.emergency_unlock_settings_reason_required),
                subtitle = stringResource(R.string.emergency_unlock_settings_reason_required_subtitle),
                checked = uiState.reasonRequired,
                onCheckedChange = viewModel::setReasonRequired,
            )
        }
    }
}

@Composable
private fun SettingSectionTitle(text: String) {
    Text(
        text = text,
        color = KeepTheme.colors.onSurfaceVariant,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = KeepTheme.colors.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = KeepTheme.colors.onTertiaryContainer, fontSize = 12.sp)
        }
        KeepSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DurationOptionRow(
    minutes: Int,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = stringResource(R.string.emergency_unlock_duration_minutes, minutes),
            color = KeepTheme.colors.onSurfaceVariant,
        )
    }
}
