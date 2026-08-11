package com.uiery.keep.feature.goallock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.kds.KeepBadge
import com.uiery.kds.KeepBadgeTone
import com.uiery.kds.KeepBadgeVariant
import com.uiery.kds.KeepLabel
import com.uiery.kds.KeepLabelSize
import com.uiery.kds.KeepLabelTone
import com.uiery.kds.KeepLabelWeight
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonVariant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.uiery.kds.KeepIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import com.uiery.kds.KeepTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.ui.component.SetupAppRow
import com.uiery.keep.ui.component.SetupGroupCard
import com.uiery.keep.ui.component.SetupSecondaryButton
import com.uiery.keep.ui.component.SetupSectionCaption
import com.uiery.keep.ui.component.SetupSectionHeader
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalLockDetailScreen(
    viewModel: GoalLockDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateEdit: (Long) -> Unit,
) {
    val state by viewModel.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshForToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    viewModel.collectSideEffect { effect ->
        when (effect) {
            GoalLockDetailSideEffect.NotFound,
            GoalLockDetailSideEffect.Ended,
            -> onNavigateBack()
        }
    }

    if (state.showEndConfirmation) {
        KeepConfirmationDialog(
            title = stringResource(id = R.string.goal_lock_detail_end_cta),
            message = stringResource(id = R.string.goal_lock_detail_end_confirmation),
            confirmLabel = stringResource(id = R.string.goal_lock_detail_end_confirm),
            dismissLabel = stringResource(id = R.string.goal_lock_detail_end_cancel),
            confirmTone = KeepConfirmationTone.Critical,
            onConfirm = viewModel::confirmEndGoalLock,
            onDismiss = viewModel::cancelEndGoalLock,
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KeepTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.goal_lock_detail_title),
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    KeepIconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(id = R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    val goalLock = state.goalLock
                    if (
                        goalLock != null &&
                        state.canEdit
                    ) {
                        KeepTextButton(onClick = { onNavigateEdit(goalLock.id) }) {
                            Text(
                                text = stringResource(id = R.string.goal_lock_detail_edit),
                                color = KeepTheme.colors.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        when {
            state.isLoading -> Text(
                modifier = Modifier.padding(paddingValues).padding(20.dp),
                text = stringResource(id = R.string.goal_lock_detail_loading),
                color = KeepTheme.colors.onSurfaceVariant,
            )
            state.goalLock == null -> SetupGroupCard(
                modifier = Modifier.padding(paddingValues).padding(20.dp),
            ) {
                SetupSectionCaption(text = stringResource(id = R.string.goal_lock_detail_load_error))
                Spacer(modifier = Modifier.height(12.dp))
                SetupSecondaryButton(
                    text = stringResource(id = R.string.goal_lock_detail_retry),
                    onClick = viewModel::retryLoad,
                )
            }
            else -> GoalLockDetailContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                state = state,
                onRequestEnd = viewModel::requestEndGoalLock,
                onRetry = viewModel::retryLoad,
            )
        }
    }
}

@Composable
internal fun GoalLockDetailContent(
    state: GoalLockDetailUiState,
    onRequestEnd: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goalLock = state.goalLock ?: return
    val presentation = state.presentation ?: return
    val statusText = goalLockStatusLabel(presentation.runtimeStatus)
    val progressText = goalLockProgressLabel(presentation.progress)
    val modeText = goalLockDetailModeLabel(goalLock.lockMode)
    val periodText = stringResource(
        id = R.string.goal_lock_detail_period_value,
        goalLock.startDate,
        goalLock.endDate,
        presentation.totalDurationDays,
    )
    val appsText = stringResource(
        id = R.string.goal_lock_edit_apps_summary,
        goalLock.selectedPackages.size,
    )
    val summary = buildGoalLockDetailAccessibilityDescription(
        goalName = goalLock.goalName,
        summaryText = listOf(progressText, periodText, modeText, appsText).joinToString(", "),
        statusText = statusText,
    )

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        SetupGroupCard(
            modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
        ) {
            GoalLockStatusBadge(
                text = statusText,
                status = presentation.runtimeStatus,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = goalLock.goalName,
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progressText,
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 15.sp,
            )
        }

        SetupGroupCard {
            SetupSectionHeader(title = stringResource(id = R.string.goal_lock_detail_information))
            Spacer(modifier = Modifier.height(14.dp))
            GoalLockDetailRow(
                label = stringResource(id = R.string.goal_lock_detail_duration_label),
                value = periodText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GoalLockDetailRow(
                label = stringResource(id = R.string.goal_lock_detail_lock_mode_label),
                value = modeText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GoalLockDetailRow(
                label = stringResource(id = R.string.goal_lock_detail_apps_label),
                value = appsText,
            )
        }

        SetupGroupCard {
            SetupSectionHeader(
                title = stringResource(id = R.string.goal_lock_detail_apps_label),
                valueLabel = goalLock.selectedPackages.size.toString(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goalLock.selectedPackages.sorted().forEach { packageName ->
                    SetupAppRow(
                        packageName = packageName,
                        fallbackLabel = packageName,
                    )
                }
            }
        }

        if (state.error != null) {
            SetupGroupCard {
                SetupSectionCaption(
                    text = stringResource(
                        id = if (state.error == GoalLockDetailError.End) {
                            R.string.goal_lock_detail_end_error
                        } else {
                            R.string.goal_lock_detail_load_error
                        },
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SetupSecondaryButton(
                    text = stringResource(id = R.string.goal_lock_detail_retry),
                    onClick = onRetry,
                )
            }
        }

        if (state.canEnd) {
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.goal_lock_detail_end_cta),
                onClick = onRequestEnd,
                variant = KeepButtonVariant.CriticalSolid,
                bottomSpacing = false,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GoalLockStatusBadge(
    text: String,
    status: GoalLockRuntimeStatus,
) {
    KeepBadge(
        text = text,
        tone = when (status) {
            GoalLockRuntimeStatus.Pending,
            GoalLockRuntimeStatus.Completed,
            -> KeepBadgeTone.Neutral
            GoalLockRuntimeStatus.Active -> KeepBadgeTone.Brand
            GoalLockRuntimeStatus.EndedEarly -> KeepBadgeTone.Critical
        },
        variant = KeepBadgeVariant.Weak,
    )
}

@Composable
private fun GoalLockDetailRow(label: String, value: String) {
    Column {
        KeepLabel(
            text = label,
            tone = KeepLabelTone.Muted,
            size = KeepLabelSize.Small,
        )
        Spacer(modifier = Modifier.height(3.dp))
        KeepLabel(
            text = value,
            size = KeepLabelSize.Large,
            weight = KeepLabelWeight.Strong,
        )
    }
}

@Composable
private fun goalLockStatusLabel(status: GoalLockRuntimeStatus): String = stringResource(
    id = when (status) {
        GoalLockRuntimeStatus.Pending -> R.string.goal_lock_detail_status_pending
        GoalLockRuntimeStatus.Active -> R.string.goal_lock_detail_status_active
        GoalLockRuntimeStatus.Completed -> R.string.goal_lock_detail_status_completed
        GoalLockRuntimeStatus.EndedEarly -> R.string.goal_lock_detail_status_ended
    },
)

@Composable
private fun goalLockProgressLabel(progress: GoalLockProgress): String = when (progress) {
    is GoalLockProgress.Pending -> stringResource(
        id = R.string.goal_lock_detail_progress_pending,
        progress.daysUntilStart,
    )
    is GoalLockProgress.Active -> stringResource(
        id = R.string.goal_lock_detail_progress_active,
        progress.remainingDaysIncludingToday,
    )
    is GoalLockProgress.Completed -> stringResource(
        id = R.string.goal_lock_detail_progress_completed,
        progress.startDate,
        progress.endDate,
    )
    GoalLockProgress.EndedEarly -> stringResource(id = R.string.goal_lock_detail_progress_ended)
}

@Composable
internal fun goalLockDetailModeLabel(lockMode: GoalLockMode): String = when (lockMode) {
    GoalLockMode.AllDay -> stringResource(id = R.string.goal_lock_detail_lock_mode_all_day)
    is GoalLockMode.Scheduled -> stringResource(
        id = R.string.goal_lock_edit_schedule_summary,
        lockMode.repeatDays
            .sortedBy { it.value }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
        lockMode.startTime,
        lockMode.endTime,
    )
}

fun buildGoalLockDetailAccessibilityDescription(
    goalName: String,
    summaryText: String,
    statusText: String,
): String = listOf(goalName.trim(), summaryText.trim(), statusText.trim())
    .filter(String::isNotBlank)
    .joinToString(", ")
