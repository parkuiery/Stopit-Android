package com.uiery.keep.feature.onboarding.result

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.onboarding.OnboardingActionStack
import com.uiery.keep.feature.onboarding.OnboardingBottomActionBar
import com.uiery.keep.feature.onboarding.proposal.formatTime
import com.uiery.keep.feature.routine.RoutineAlarmPermissionSettingsLauncher
import com.uiery.keep.feature.routine.RoutineAlarmPermissionSettingsLaunchResult
import com.uiery.keep.feature.routine.createAppDetailsSettingsIntent
import com.uiery.keep.feature.routine.createExactAlarmSettingsIntent
import com.uiery.keep.util.formatWeekdayShort
import java.time.DayOfWeek
import java.util.Locale
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun PromiseResultScreen(
    onNavigateProposal: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateLock: (String?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PromiseResultViewModel = hiltViewModel(),
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    viewModel.collectSideEffect { effect ->
        when (effect) {
            PromiseResultSideEffect.NavigateProposal -> onNavigateProposal()
            PromiseResultSideEffect.NavigateHome -> onNavigateHome()
            is PromiseResultSideEffect.NavigateLock -> onNavigateLock(effect.lockTime, false)
            PromiseResultSideEffect.OpenExactAlarmSettings -> {
                if (RoutineAlarmPermissionSettingsLauncher.open(
                    exactAlarmTarget = createExactAlarmSettingsIntent(context.packageName),
                    appDetailsTarget = createAppDetailsSettingsIntent(context.packageName),
                    launch = context::startActivity,
                ) == RoutineAlarmPermissionSettingsLaunchResult.Unavailable) {
                    viewModel.onExactAlarmSettingsUnavailable()
                }
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.load() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val observedLifecycle = ((context as? Activity) as? LifecycleOwner)?.lifecycle
        ?: lifecycleOwner.lifecycle
    DisposableEffect(observedLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumeFromExactAlarm()
        }
        observedLifecycle.addObserver(observer)
        onDispose { observedLifecycle.removeObserver(observer) }
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = KeepTheme.colors.background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.kind == PromiseResultKind.Loading) {
                    KeepCircularProgressIndicator(color = KeepTheme.colors.primary)
                } else {
                    Text(
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                        text = stringResource(state.titleRes()),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = KeepTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    PromiseSummaryCard(state)
                    if (state.showScheduledGuidance) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.first_promise_result_scheduled_guidance),
                            style = MaterialTheme.typography.bodyLarge,
                            color = KeepTheme.colors.surfaceVariant,
                        )
                    }
                    if (state.practiceFailed || state.activationFailed) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            text = stringResource(
                                if (state.practiceFailed) R.string.first_promise_result_practice_failed
                                else R.string.first_promise_result_activation_failed,
                            ),
                            color = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
            ResultActions(state, viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PromiseSummaryCard(state: PromiseResultUiState) {
    KeepCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.first_promise_result_app, state.appLabel),
                style = MaterialTheme.typography.titleMedium,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.first_promise_result_start, formatTime(state.startMinutes)),
                color = KeepTheme.colors.surfaceVariant,
            )
            Text(
                text = stringResource(R.string.first_promise_result_duration),
                color = KeepTheme.colors.surfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.first_promise_result_repeat,
                    state.repeatDays.sorted().joinToString(" · ") {
                        formatWeekdayShort(DayOfWeek.of(it), Locale.getDefault())
                    },
                ),
                color = KeepTheme.colors.surfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultActions(state: PromiseResultUiState, viewModel: PromiseResultViewModel) {
    when (state.kind) {
        PromiseResultKind.Enabled -> {
            if (state.showPractice) {
                OnboardingActionStack(
                    primaryText = stringResource(
                        if (state.practiceFailed) R.string.first_promise_result_retry_practice
                        else R.string.first_promise_result_practice,
                    ),
                    secondaryText = stringResource(R.string.first_promise_result_scheduled),
                    primaryEnabled = !state.isBusy,
                    secondaryEnabled = !state.isBusy,
                    bottomSpacing = false,
                    onPrimaryClick = viewModel::startPractice,
                    onSecondaryClick = viewModel::continueAtScheduledTime,
                )
            } else {
                OnboardingBottomActionBar {
                    KeepTextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy,
                        onClick = viewModel::continueAtScheduledTime,
                    ) { Text(stringResource(R.string.first_promise_result_scheduled)) }
                }
            }
        }
        PromiseResultKind.PermissionRequired -> {
            OnboardingActionStack(
                primaryText = stringResource(R.string.first_promise_result_enable),
                secondaryText = stringResource(R.string.first_promise_result_later),
                primaryEnabled = !state.isBusy,
                secondaryEnabled = !state.isBusy,
                bottomSpacing = false,
                onPrimaryClick = viewModel::requestExactAlarm,
                onSecondaryClick = viewModel::continueLater,
            )
        }
        PromiseResultKind.Disabled -> {
            OnboardingBottomActionBar {
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.first_promise_result_home),
                    enabled = !state.isBusy,
                    bottomSpacing = false,
                    onClick = viewModel::continueHome,
                )
            }
        }
        PromiseResultKind.PersistFailed -> {
            OnboardingActionStack(
                primaryText = stringResource(R.string.first_promise_result_retry_save),
                secondaryText = stringResource(R.string.first_promise_result_edit),
                primaryEnabled = state.canRetry && !state.isBusy,
                secondaryEnabled = state.canEdit && !state.isBusy,
                bottomSpacing = false,
                onPrimaryClick = viewModel::retryPersistence,
                onSecondaryClick = viewModel::editPromise,
            )
        }
        PromiseResultKind.Loading -> Unit
    }
}

private fun PromiseResultUiState.titleRes(): Int = when (kind) {
    PromiseResultKind.Enabled -> R.string.first_promise_result_ready_title
    PromiseResultKind.Disabled, PromiseResultKind.PermissionRequired ->
        R.string.first_promise_result_saved_title
    PromiseResultKind.PersistFailed -> R.string.first_promise_result_save_failed_title
    PromiseResultKind.Loading -> R.string.first_promise_result_saved_title
}
