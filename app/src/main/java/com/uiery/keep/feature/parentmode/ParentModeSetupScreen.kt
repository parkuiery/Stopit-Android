package com.uiery.keep.feature.parentmode

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.SetupAppRow
import com.uiery.keep.ui.component.SetupChip
import com.uiery.keep.ui.component.SetupGroupCard
import com.uiery.keep.ui.component.SetupHero
import com.uiery.keep.ui.component.SetupSecondaryButton
import com.uiery.keep.ui.component.SetupSectionCaption
import com.uiery.keep.ui.component.SetupSectionHeader
import com.uiery.keep.ui.component.SetupTextField

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
                title = {
                    Text(
                        text = stringResource(id = R.string.parent_mode_setup_title),
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
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SetupHero(
                iconResId = R.drawable.ic_parent_mode,
                title = stringResource(id = R.string.parent_mode_setup_headline),
                subtitle = stringResource(id = R.string.parent_mode_setup_description),
            )

            // Allowed time
            SetupGroupCard {
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
                            onClick = { viewModel.setDurationMinutes(minutes) },
                        )
                    }
                }
            }

            // Allowed apps
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
                        onClick = viewModel::loadAllowedAppsFromCurrentSelection,
                    )
                    SetupSecondaryButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.parent_mode_setup_adjust_apps),
                        onClick = { isAppSelectionSheetVisible = true },
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

            // Guardian PIN
            SetupGroupCard {
                SetupSectionHeader(title = stringResource(id = R.string.parent_mode_setup_pin_label))
                Spacer(modifier = Modifier.height(6.dp))
                SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_pin_required_notice))
                Spacer(modifier = Modifier.height(12.dp))
                SetupTextField(
                    value = state.guardianPin,
                    onValueChange = viewModel::updateGuardianPin,
                    placeholder = stringResource(id = R.string.parent_mode_setup_pin_label),
                    keyboardType = KeyboardType.NumberPassword,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                SetupSectionCaption(text = stringResource(id = R.string.parent_mode_setup_pin_helper))
                Spacer(modifier = Modifier.height(12.dp))
                SetupTextField(
                    value = state.guardianPinConfirmation,
                    onValueChange = viewModel::updateGuardianPinConfirmation,
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

            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.parent_mode_setup_start),
                enabled = state.canAttemptStart,
                onClick = viewModel::startParentModeFromSetupInput,
            )
            SetupSecondaryButton(
                text = stringResource(id = R.string.parent_mode_setup_back_to_menu),
                onClick = onNavigateBack,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
