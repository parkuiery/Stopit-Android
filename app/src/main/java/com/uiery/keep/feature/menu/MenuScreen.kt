package com.uiery.keep.feature.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import androidx.compose.material3.ExperimentalMaterial3Api
import com.uiery.kds.KeepDivider
import androidx.compose.material3.Icon
import com.uiery.kds.KeepIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.uiery.kds.KeepTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.keep.BuildConfig
import com.uiery.keep.MobileAdsPrivacyOptions
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.analytics.toMetadata
import com.uiery.keep.feature.menu.component.MenuItem
import com.uiery.keep.feature.menu.component.MenuToggleItem
import com.uiery.keep.showMobileAdsPrivacyOptions
import com.uiery.keep.util.findActivity
import com.uiery.kds.theme.KeepTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    menuViewModel: MenuViewModel = hiltViewModel(),
    onNavigateDevTool: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateRoutine: () -> Unit,
    onNavigateGoalLockCreation: () -> Unit,
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit,
    onNavigateParentModeSetup: () -> Unit,
    onNavigateLockHistory: () -> Unit,
    onNavigateEmergencyUnlockSettings: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val goalLockNavigationGate = remember { GoalLockNavigationGate() }
    val preventUninstall by menuViewModel.preventUninstall.collectAsStateWithLifecycle()
    val isBlocking by menuViewModel.isBlocking.collectAsStateWithLifecycle()
    val isPrivacyOptionsRequired by MobileAdsPrivacyOptions.isRequired.collectAsStateWithLifecycle()
    val monetizationInterestTitle = stringResource(id = R.string.monetization_interest_menu_title)
    val monetizationInterestMessage = stringResource(id = R.string.monetization_interest_menu_message)
    LaunchedEffect(menuViewModel) {
        menuViewModel.onMonetizationInterestCardShown()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            KeepTopAppBar(
                navigationIcon = {
                    KeepIconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                title = { },
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 메뉴 항목 수는 고정이 아니다. 광고 개인정보 항목이 동의 상태에 따라 붙고, 글자 크기
            // 설정이 커지면 행 높이도 자란다. 스크롤 표면이 없으면 넘친 부분에 닿을 방법이 없다.
            // 배너는 지금처럼 하단에 고정되도록 스크롤 밖에 둔다.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
            MenuItem(
                icon = R.drawable.ic_routine,
                title = stringResource(id = R.string.routine),
                onClick = onNavigateRoutine,
            )
            MenuItem(
                icon = R.drawable.ic_goal_lock,
                title = stringResource(id = R.string.goal_lock_menu_title),
                onClick = {
                    if (goalLockNavigationGate.tryEnter()) {
                        coroutineScope.launch {
                            val currentGoalLockId = menuViewModel.getCurrentGoalLockId()
                            if (currentGoalLockId == null) {
                                onNavigateGoalLockCreation()
                            } else {
                                onNavigateGoalLockDetail(currentGoalLockId)
                            }
                        }
                    }
                },
            )
            MenuItem(
                icon = R.drawable.ic_parent_mode,
                title = stringResource(id = R.string.parent_mode_menu_title),
                onClick = onNavigateParentModeSetup,
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
            if (isPrivacyOptionsRequired) {
                MenuItem(
                    icon = R.drawable.ic_shield,
                    title = stringResource(id = R.string.ad_privacy_options),
                    onClick = {
                        context.findActivity()?.let { activity ->
                            showMobileAdsPrivacyOptions(activity) { errorMessage ->
                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
            MenuItem(
                icon = R.drawable.ic_letter,
                title = stringResource(id = R.string.contact_us),
                onClick = {
                    menuViewModel.onSupportContactStarted()
                    sendCustomerEmail(
                        context = context,
                        onFallbackUsed = { menuViewModel.onSupportContactClipboardFallbackUsed() },
                    )
                }
            )
            KeepCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "$monetizationInterestTitle, $monetizationInterestMessage"
                    }
                    .clickable(onClick = {
                        menuViewModel.onMonetizationInterestCardClicked()
                        menuViewModel.onSupportContactStarted()
                        sendCustomerEmail(
                            context = context,
                            onFallbackUsed = { menuViewModel.onSupportContactClipboardFallbackUsed() },
                        )
                    }),
                variant = KeepCardVariant.NeutralWeak,
                bordered = true,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    text = monetizationInterestTitle,
                    color = KeepTheme.colors.onSurface,
                    textAlign = TextAlign.Start,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                    text = monetizationInterestMessage,
                    color = KeepTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            KeepDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                thickness = 1.dp,
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
            }
            TrackedBannerAd(
                modifier = Modifier.fillMaxWidth(),
                metadata = AdPlacement.MenuBottom.toMetadata(
                    screenName = KeepAnalyticsScreen.MENU,
                    screenContext = "settings",
                ),
            )
        }
    }
}

internal class GoalLockNavigationGate {
    private var isInProgress = false

    fun tryEnter(): Boolean {
        if (isInProgress) return false
        isInProgress = true
        return true
    }
}

private fun sendCustomerEmail(
    context: Context,
    onFallbackUsed: () -> Unit,
) {
    val diagnostics = buildSupportContactDiagnostics(
        versionName = BuildConfig.VERSION_NAME,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        deviceModel = Build.MODEL,
    )
    val emailSelectorIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(STOPIT_SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        putExtra(Intent.EXTRA_TEXT, "\n\n\n\n-\n$diagnostics")
        selector = emailSelectorIntent
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val fallbackText = buildSupportContactFallbackText(
            supportEmail = STOPIT_SUPPORT_EMAIL,
            diagnostics = diagnostics,
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.contact_us), fallbackText),
        )
        onFallbackUsed()
        Toast.makeText(
            context,
            context.getString(R.string.support_contact_fallback_copied),
            Toast.LENGTH_SHORT
        ).show()
    }
}
