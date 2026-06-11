package com.uiery.keep.feature.parentmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParentModeSetupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParentModeSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.loadAllowedAppsFromCurrentSelection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                title = { Text(text = stringResource(id = R.string.parent_mode_setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                ),
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val activeSession = state.activeSession
            if (activeSession == null) {
                ParentModeSetupForm(
                    state = state,
                    onDurationSelected = viewModel::setDurationMinutes,
                    onGuardianPinChanged = viewModel::updateGuardianPin,
                    onGuardianPinConfirmationChanged = viewModel::updateGuardianPinConfirmation,
                    onStart = viewModel::startParentModeFromSetupInput,
                    onNavigateBack = onNavigateBack,
                )
            } else {
                ParentModeActiveControls(
                    session = activeSession,
                    onRefresh = viewModel::refreshActiveSessionStatus,
                    onExtend = viewModel::extendActiveSessionByTenMinutes,
                    onEnd = viewModel::endActiveSessionFromSetupInput,
                    onNavigateBack = onNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun ParentModeSetupForm(
    state: ParentModeSetupUiState,
    onDurationSelected: (Int) -> Unit,
    onGuardianPinChanged: (String) -> Unit,
    onGuardianPinConfirmationChanged: (String) -> Unit,
    onStart: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Text(
        text = stringResource(id = R.string.parent_mode_setup_headline),
        color = KeepTheme.colors.onSurface,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(id = R.string.parent_mode_setup_description),
        color = KeepTheme.colors.onSurfaceVariant,
    )
    Text(
        text = stringResource(id = R.string.parent_mode_setup_duration_label),
        color = KeepTheme.colors.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ParentModeDurationButton(
            durationMinutes = 10,
            selectedDurationMinutes = state.durationMinutes,
            onDurationSelected = onDurationSelected,
        )
        ParentModeDurationButton(
            durationMinutes = 20,
            selectedDurationMinutes = state.durationMinutes,
            onDurationSelected = onDurationSelected,
        )
        ParentModeDurationButton(
            durationMinutes = 30,
            selectedDurationMinutes = state.durationMinutes,
            onDurationSelected = onDurationSelected,
        )
    }
    Text(
        text = stringResource(
            id = R.string.parent_mode_setup_allowed_apps_summary,
            state.allowedApps.size,
        ),
        color = KeepTheme.colors.onSurface,
    )
    Text(
        text = stringResource(id = R.string.parent_mode_setup_pin_required_notice),
        color = KeepTheme.colors.onSurfaceVariant,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.guardianPin,
        onValueChange = onGuardianPinChanged,
        label = { Text(text = stringResource(id = R.string.parent_mode_setup_pin_label)) },
        supportingText = { Text(text = stringResource(id = R.string.parent_mode_setup_pin_helper)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.guardianPinConfirmation,
        onValueChange = onGuardianPinConfirmationChanged,
        label = { Text(text = stringResource(id = R.string.parent_mode_setup_pin_confirm_label)) },
        isError = state.guardianPinConfirmation.isNotEmpty() && state.pinState == ParentModePinState.Failed,
        supportingText = {
            if (state.guardianPinConfirmation.isNotEmpty() && state.pinState == ParentModePinState.Failed) {
                Text(text = stringResource(id = R.string.parent_mode_setup_pin_mismatch))
            }
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canAttemptStart,
        onClick = onStart,
    ) {
        Text(text = stringResource(id = R.string.parent_mode_setup_start))
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateBack,
    ) {
        Text(text = stringResource(id = R.string.parent_mode_setup_back_to_menu))
    }
}

@Composable
private fun RowScope.ParentModeDurationButton(
    durationMinutes: Int,
    selectedDurationMinutes: Int,
    onDurationSelected: (Int) -> Unit,
) {
    val selected = durationMinutes == selectedDurationMinutes
    val label = stringResource(id = R.string.parent_mode_setup_duration_option, durationMinutes)
    if (selected) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = { onDurationSelected(durationMinutes) },
        ) {
            Text(text = label)
        }
    } else {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onDurationSelected(durationMinutes) },
        ) {
            Text(text = label)
        }
    }
}

@Composable
private fun ParentModeActiveControls(
    session: ParentModeSession,
    onRefresh: () -> Unit,
    onExtend: () -> Unit,
    onEnd: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(session.expiresAtMillis, session.state) {
        onRefresh()
    }
    val statusTextRes = when (session.state) {
        ParentModeSessionState.Active -> R.string.parent_mode_active_title
        ParentModeSessionState.Expired -> R.string.parent_mode_expired_title
        ParentModeSessionState.UnlockedByPin -> R.string.parent_mode_ended_title
        ParentModeSessionState.Setup,
        ParentModeSessionState.Cancelled,
        -> R.string.parent_mode_setup_title
    }
    Text(
        text = stringResource(id = statusTextRes),
        color = KeepTheme.colors.onSurface,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(
            id = R.string.parent_mode_active_summary,
            session.durationMinutes,
            session.allowedApps.size,
        ),
        color = KeepTheme.colors.onSurfaceVariant,
    )
    Text(
        text = stringResource(id = R.string.parent_mode_active_pin_notice),
        color = KeepTheme.colors.onSurfaceVariant,
    )
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = session.state == ParentModeSessionState.Active,
        onClick = onExtend,
    ) {
        Text(text = stringResource(id = R.string.parent_mode_active_extend_ten_minutes))
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = session.state == ParentModeSessionState.Active,
        onClick = onEnd,
    ) {
        Text(text = stringResource(id = R.string.parent_mode_active_end_now))
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateBack,
    ) {
        Text(text = stringResource(id = R.string.parent_mode_setup_back_to_menu))
    }
}
