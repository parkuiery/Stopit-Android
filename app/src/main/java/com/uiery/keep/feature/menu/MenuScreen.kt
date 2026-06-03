package com.uiery.keep.feature.menu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.menu.component.MenuItem
import com.uiery.keep.feature.menu.component.MenuToggleItem
import androidx.core.net.toUri
import com.uiery.keep.BuildConfig
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.AdPlacementMetadata
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.analytics.KeepAnalyticsScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    menuViewModel: MenuViewModel = hiltViewModel(),
    onNavigateDevTool: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateRoutine: () -> Unit,
    onNavigateLockHistory: () -> Unit,
    onNavigateEmergencyUnlockSettings: () -> Unit,
) {
    val context = LocalContext.current
    val preventUninstall by menuViewModel.preventUninstall.collectAsStateWithLifecycle()
    val isBlocking by menuViewModel.isBlocking.collectAsStateWithLifecycle()
    LaunchedEffect(menuViewModel) {
        menuViewModel.onMonetizationInterestCardShown()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = Color(0xFFFE9E0B),
                        )
                    }
                },
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                )
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
//            if (isTestMode()) {
//                MenuItem(
//                    icon = R.drawable.laptop,
//                    title = "Developer Options",
//                    onClick = onNavigateDevTool,
//                )
//            }
            /*if(isKorean) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://forms.gle/MsKwrgFr7fYQQYW1A".toUri())
                            context.startActivity(intent)
                        }),
                    colors = CardDefaults.cardColors(
                        containerColor = KeepTheme.colors.onTertiary,
                    ),
                    border = BorderStroke(1.dp, KeepTheme.colors.onTertiaryContainer),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                         text = stringResource(id = R.string.feature_request_message),
                        color = KeepTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }*/
            MenuItem(
                icon = R.drawable.ic_routine,
                title = stringResource(id = R.string.routine),
                onClick = onNavigateRoutine,
            )
            MenuItem(
                icon = R.drawable.ic_local_history,
                title = stringResource(id = R.string.lock_history_menu_title),
                onClick = onNavigateLockHistory,
            )
            MenuItem(
                icon = R.drawable.ic_emergency,
                title = stringResource(id = R.string.emergency_unlock_settings_title),
                onClick = onNavigateEmergencyUnlockSettings,
            )
            MenuItem(
                icon = R.drawable.ic_letter,
                title = stringResource(id = R.string.contact_us),
                onClick = { sendCustomerEmail(context) }
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clickable(onClick = {
                        menuViewModel.onMonetizationInterestCardClicked()
                        sendCustomerEmail(context)
                    }),
                colors = CardDefaults.cardColors(
                    containerColor = KeepTheme.colors.onTertiary,
                ),
                border = BorderStroke(1.dp, KeepTheme.colors.onTertiaryContainer),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    text = stringResource(id = R.string.monetization_interest_menu_title),
                    color = KeepTheme.colors.onSurface,
                    textAlign = TextAlign.Start,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                    text = stringResource(id = R.string.monetization_interest_menu_message),
                    color = KeepTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                thickness = 1.dp,
                color = KeepTheme.colors.onTertiaryContainer,
            )
            MenuToggleItem(
                icon = R.drawable.ic_shield,
                title = stringResource(id = R.string.prevent_uninstall),
                subtitle = stringResource(id = R.string.prevent_uninstall_subtitle),
                checked = preventUninstall,
                enabled = !isBlocking,
                onCheckedChange = { enabled ->
                    menuViewModel.setPreventUninstall(enabled)
                    if (enabled) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.prevent_uninstall_enabled),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            TrackedBannerAd(
                modifier = Modifier.fillMaxWidth(),
                metadata = AdPlacementMetadata(
                    screenName = KeepAnalyticsScreen.MENU,
                    screenContext = "settings",
                    placement = AdPlacement.MenuBottom.analyticsPlacement,
                    adUnitId = AdPlacement.MenuBottom.adUnitId,
                ),
            )
        }
    }
}

private fun sendCustomerEmail(context: Context) {
    val emailSelectorIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf("parkuiery@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        putExtra(
            Intent.EXTRA_TEXT,
            "\n\n\n\n-\nVersion ${BuildConfig.VERSION_NAME}\nAndroid OS ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}),${Build.MODEL}"
        )
        selector = emailSelectorIntent
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.email_app_not_installed),
            Toast.LENGTH_SHORT
        ).show()
    }
}
