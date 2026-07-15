package com.uiery.keep.feature.onboarding.permission

import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.onboarding.OnboardingPermissionContext
import com.uiery.keep.ui.component.PermissionSettingDialog
import com.uiery.keep.util.hasAccessibilityPermission
import com.uiery.keep.util.requestAccessibilityPermission

@Composable
fun PermissionSettingScreen(
    modifier: Modifier = Modifier,
    viewModel: PermissionSettingViewModel = hiltViewModel(),
    onNavigateNotificationSetting: () -> Unit,
    flowContext: OnboardingPermissionContext = OnboardingPermissionContext.Control,
    onNavigateBack: () -> Unit = {},
) {
    val androidContext = LocalContext.current
    var openAlertDialog by remember { mutableStateOf(false) }
    var promiseStartMinutes by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = flowContext == OnboardingPermissionContext.FirstPromise) {
        viewModel.onFirstPromiseBack(onNavigateBack)
    }

    LaunchedEffect(Unit) {
        viewModel.onStepViewed()
        if (flowContext == OnboardingPermissionContext.FirstPromise) {
            viewModel.loadFirstPromise(
                accessibilityGranted = hasAccessibilityPermission(androidContext),
                onLoaded = { promiseStartMinutes = it },
                onNavigateNotification = onNavigateNotificationSetting,
            )
        }
    }

    if (openAlertDialog) {
        PermissionSettingDialog(
            onDismissRequest = { openAlertDialog = false },
            onConfirmation = {
                openAlertDialog = false
                viewModel.onPermissionSettingsOpened()
                requestAccessibilityPermission(androidContext)
            },
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            val isReady = flowContext == OnboardingPermissionContext.Control || promiseStartMinutes != null
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
            Text(
                modifier = Modifier.padding(top = 36.dp).semantics { heading() },
                text = if (flowContext == OnboardingPermissionContext.Control) {
                    stringResource(id = R.string.accessibility_permission_required)
                } else if (promiseStartMinutes == null) {
                    stringResource(R.string.first_promise_accessibility_loading)
                } else {
                    stringResource(
                        R.string.first_promise_accessibility_title,
                        com.uiery.keep.feature.onboarding.proposal.formatTime(checkNotNull(promiseStartMinutes)),
                    )
                },
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!isReady) {
                val loadingDescription = stringResource(R.string.first_promise_accessibility_loading)
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = loadingDescription
                        stateDescription = loadingDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                    color = KeepTheme.colors.primary,
                )
            } else {
            Text(
                text = if (flowContext == OnboardingPermissionContext.Control) {
                    stringResource(id = R.string.accessibility_permission_description)
                } else {
                    stringResource(R.string.first_promise_accessibility_description)
                },
                color = KeepTheme.colors.surfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.shield),
                    contentDescription = null,
                )
                Text(
                    text = stringResource(id = R.string.permission_usage_note),
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = stringResource(id = R.string.accessibility_setup_guide),
                color = KeepTheme.colors.surfaceVariant,
                style = LocalTextStyle.current.copy(lineHeight = 24.sp)
            )
            }
            Spacer(modifier = Modifier.height(24.dp))
            }
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.allow_permission),
                enabled = isReady,
                onClick = {
                    if (hasAccessibilityPermission(androidContext)) {
                        if (flowContext == OnboardingPermissionContext.Control) {
                            viewModel.onPermissionGranted()
                            onNavigateNotificationSetting()
                        } else {
                            viewModel.onFirstPromisePermissionGranted(onNavigateNotificationSetting)
                        }
                    } else {
                        openAlertDialog = true
                    }
                },
            )
        }
    }
}
