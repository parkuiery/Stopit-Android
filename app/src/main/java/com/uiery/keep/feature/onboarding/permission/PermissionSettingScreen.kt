package com.uiery.keep.feature.onboarding.permission

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.onboarding.permission.component.PermissionSettingDialog
import com.uiery.keep.util.hasAccessibilityPermission
import com.uiery.keep.util.requestAccessibilityPermission

@Composable
fun PermissionSettingScreen(
    modifier: Modifier = Modifier,
    viewModel: PermissionSettingViewModel = hiltViewModel(),
    onNavigateNotificationSetting: () -> Unit,
) {
    val context = LocalContext.current
    var openAlertDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onStepViewed()
    }

    if (openAlertDialog) {
        PermissionSettingDialog(
            onDismissRequest = { openAlertDialog = false },
            onConfirmation = {
                openAlertDialog = false
                viewModel.onPermissionSettingsOpened()
                requestAccessibilityPermission(context)
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
            Text(
                modifier = Modifier.padding(top = 36.dp),
                text = stringResource(id = R.string.accessibility_permission_required),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.accessibility_permission_description),
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
            Spacer(modifier = Modifier.weight(1f))
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.allow_permission),
                onClick = {
                    if (hasAccessibilityPermission(context)) {
                        viewModel.onPermissionGranted()
                        onNavigateNotificationSetting()
                    } else {
                        openAlertDialog = true
                    }
                },
            )
        }
    }
}
