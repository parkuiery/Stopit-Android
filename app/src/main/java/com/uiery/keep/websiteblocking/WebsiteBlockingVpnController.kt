package com.uiery.keep.websiteblocking

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.DomainName
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingConflictPolicy
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingOwnership
import com.uiery.keep.domain.websiteblocking.WebsiteBlockingRuntimeDecision

/**
 * 잠금 상태가 바뀔 때 DNS 차단 VPN 을 따라 세우고 내린다.
 *
 * 시스템 VPN 동의는 액티비티 결과로만 받을 수 있어서 이 판단은 화면에 붙어 있어야 한다.
 * 대신 서비스에는 마감 시각을 함께 넘기므로, 화면이 사라진 뒤에도 잠금은 제때 풀린다.
 *
 * 동의를 거부해도 잠금 자체는 진행한다. 앱 차단까지 함께 취소하면 사용자가 방금 한
 * 약속을 통째로 잃는다. 대신 [onConsentDenied] 로 웹사이트는 막히지 않는다고 알린다.
 */
@Composable
fun WebsiteBlockingVpnController(
    decision: WebsiteBlockingRuntimeDecision,
    onConsentDenied: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnConsentDenied by rememberUpdatedState(onConsentDenied)
    var pendingStart by remember { mutableStateOf<WebsiteBlockingRuntimeDecision.Running?>(null) }
    // 다른 VPN 을 끊어도 되는지 사용자가 답한 결과. 잠금이 끝나면 다시 물어본다.
    var displacementApproved by remember { mutableStateOf(false) }
    var pendingDisplacement by remember { mutableStateOf<WebsiteBlockingRuntimeDecision.Running?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val start = pendingStart
        pendingStart = null
        if (result.resultCode == Activity.RESULT_OK && start != null) {
            context.startWebsiteBlocking(start.domains, start.stopAtEpochMillis)
        } else {
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
            currentOnConsentDenied()
        }
    }

    LaunchedEffect(decision, displacementApproved) {
        when (decision) {
            is WebsiteBlockingRuntimeDecision.Undecided -> Unit

            is WebsiteBlockingRuntimeDecision.Stopped -> {
                pendingStart = null
                pendingDisplacement = null
                displacementApproved = false
                if (WebsiteBlockingRuntimeState.status.value != WebsiteBlockingStatus.Inactive) {
                    context.startService(KeepDnsVpnService.stopIntent(context))
                    WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
                }
            }

            is WebsiteBlockingRuntimeDecision.Running -> {
                if (
                    WebsiteBlockingConflictPolicy.shouldAskBeforeDisplacing(
                        status = context.websiteBlockingOwnership(),
                        otherVpnActive = context.otherVpnActive(),
                        displacementApproved = displacementApproved,
                    )
                ) {
                    pendingDisplacement = decision
                    return@LaunchedEffect
                }
                val consentIntent = VpnService.prepare(context)
                if (consentIntent == null) {
                    context.startWebsiteBlocking(decision.domains, decision.stopAtEpochMillis)
                } else if (
                    // 한 번 거부한 잠금에는 동의창을 다시 띄우지 않는다. 홈에 돌아올 때마다
                    // 시스템 창이 다시 뜨면 거부가 아니라 괴롭힘이 된다. 잠금이 끝나면
                    // 상태가 Inactive 로 돌아가므로 다음 잠금에서는 다시 물어본다.
                    WebsiteBlockingRuntimeState.status.value != WebsiteBlockingStatus.ConsentDenied
                ) {
                    pendingStart = decision
                    consentLauncher.launch(consentIntent)
                }
            }
        }
    }

    pendingDisplacement?.let {
        KeepConfirmationDialog(
            title = stringResource(R.string.website_blocking_other_vpn_title),
            message = stringResource(R.string.website_blocking_other_vpn_message),
            confirmLabel = stringResource(R.string.website_blocking_other_vpn_confirm),
            dismissLabel = stringResource(R.string.website_blocking_other_vpn_skip),
            confirmTone = KeepConfirmationTone.Critical,
            onConfirm = {
                pendingDisplacement = null
                displacementApproved = true
            },
            onDismiss = {
                pendingDisplacement = null
                // 잠금은 그대로 두고 웹 차단만 포기한다. 왜 막히지 않는지는 배너가 말한다.
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Unavailable)
            },
        )
    }
}

/**
 * 다른 앱이 VPN 슬롯을 쥐고 있는지. 소유자 UID 는 시스템 권한 없이 읽을 수 없으므로,
 * 우리 서비스가 서 있지 않은데 VPN 이 존재하면 남의 것으로 본다.
 */
private fun Context.otherVpnActive(): Boolean {
    if (websiteBlockingOwnership() == WebsiteBlockingOwnership.OwnedByKeep) return false
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return false
    return connectivityManager.allNetworks.any { network ->
        connectivityManager
            .getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}

private fun Context.websiteBlockingOwnership(): WebsiteBlockingOwnership =
    if (WebsiteBlockingRuntimeState.status.value == WebsiteBlockingStatus.Active) {
        WebsiteBlockingOwnership.OwnedByKeep
    } else {
        WebsiteBlockingOwnership.NotOwnedByKeep
    }

private fun Context.startWebsiteBlocking(
    domains: Set<DomainName>,
    stopAtEpochMillis: Long?,
) {
    ContextCompat.startForegroundService(
        this,
        KeepDnsVpnService.startIntent(
            context = this,
            domains = domains,
            stopAtEpochMillis = stopAtEpochMillis,
        ),
    )
}
