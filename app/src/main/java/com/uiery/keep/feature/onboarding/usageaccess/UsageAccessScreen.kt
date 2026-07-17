package com.uiery.keep.feature.onboarding.usageaccess

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.ui.component.SetupGroupCard
import com.uiery.keep.ui.component.SetupGroupDivider
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UsageAccessScreen(
    onNavigateUsageAnalysis: () -> Unit,
    onNavigateManualAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val unavailableRequester = remember { BringIntoViewRequester() }
    viewModel.collectSideEffect { effect ->
        when (effect) {
            UsageAccessSideEffect.NavigateUsageAnalysis -> onNavigateUsageAnalysis()
            UsageAccessSideEffect.NavigateManualAppSelect -> onNavigateManualAppSelect()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.onStepViewed()
        viewModel.reconcileAfterRecreation()
    }
    LaunchedEffect(state.settingsUnavailable) {
        if (state.settingsUnavailable) unavailableRequester.bringIntoView()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = KeepTheme.colors.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    modifier = Modifier.padding(top = 36.dp).semantics { heading() },
                    text = stringResource(R.string.first_promise_usage_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(R.string.first_promise_usage_value),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(24.dp))
                SetupGroupCard {
                    PrivacyLine(R.string.first_promise_usage_seen_label, R.string.first_promise_usage_seen_body)
                    SetupGroupDivider()
                    PrivacyLine(R.string.first_promise_usage_not_seen_label, R.string.first_promise_usage_not_seen_body)
                    SetupGroupDivider()
                    PrivacyLine(R.string.first_promise_usage_local_label, R.string.first_promise_usage_local_body)
                }
                if (state.settingsUnavailable) {
                    val message = stringResource(R.string.first_promise_usage_unavailable)
                    Surface(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .bringIntoViewRequester(unavailableRequester)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                error(message)
                            },
                        color = KeepTheme.colors.onSecondary,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KeepTheme.colors.error),
                    ) {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = message,
                            color = KeepTheme.colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.first_promise_check_pattern),
                enabled = !state.isReconciling,
                bottomSpacing = false,
                onClick = { viewModel.openSettings { launchUsageAccessSettings(context) } },
            )
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                enabled = !state.isReconciling,
                onClick = viewModel::chooseManual,
            ) { Text(stringResource(R.string.first_promise_manual_setup), color = KeepTheme.colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PrivacyLine(label: Int, body: Int) {
    Text(stringResource(label), color = KeepTheme.colors.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Text(
        modifier = Modifier.padding(top = 4.dp),
        text = stringResource(body),
        color = KeepTheme.colors.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

internal fun launchUsageAccessSettings(context: Context): UsageSettingsLaunchResult = try {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    UsageSettingsLaunchResult.Opened
} catch (_: android.content.ActivityNotFoundException) {
    UsageSettingsLaunchResult.Unavailable
} catch (_: SecurityException) {
    UsageSettingsLaunchResult.Unavailable
}
