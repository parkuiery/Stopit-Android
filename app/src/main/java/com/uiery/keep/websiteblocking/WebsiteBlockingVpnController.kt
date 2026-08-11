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
import com.uiery.keep.util.AppLogger

/**
 * 잠금 상태가 바뀔 때 DNS 차단 VPN 을 따라 세우고 내린다.
 *
 * 시스템 VPN 동의는 액티비티 결과로만 받을 수 있어서 이 판단은 화면에 붙어 있어야 한다.
 * 대신 서비스에는 마감 시각을 함께 넘기므로, 화면이 사라진 뒤에도 잠금은 제때 풀린다.
 *
 * 동의를 거부해도 잠금 자체는 진행한다. 앱 차단까지 함께 취소하면 사용자가 방금 한
 * 약속을 통째로 잃는다. 대신 [onConsentDenied] 로 웹사이트는 막히지 않는다고 알린다.
 *
 * 계측 콜백([onConsentResult], [onVpnConflictResolved])은 화면이 들고 있는 ViewModel 로 넘긴다.
 * 이 컴포저블에 analytics 를 직접 주입하면 Hilt 없이 못 쓰는 UI 가 되고, 두 호출 화면이 서로
 * 다른 entry surface 를 실어야 하는 것도 여기서는 알 수 없다.
 *
 * 이 둘만 여기 있는 이유는 시스템 동의창과 충돌 확인 대화상자가 화면에서만 뜰 수 있어서다.
 * 필터 **상태** 전환은 화면과 무관하게 일어나므로 [WebsiteBlockingStatusReporter] 가 프로세스
 * 단위로 관찰한다. 그 관찰을 여기 두면 홈이 떠 있는 동안만 세게 된다.
 */
@Composable
fun WebsiteBlockingVpnController(
    decision: WebsiteBlockingRuntimeDecision,
    resumeCount: Int,
    onConsentDenied: () -> Unit,
    onConsentResult: (granted: Boolean) -> Unit = {},
    onVpnConflictResolved: (displacedOtherVpn: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnConsentDenied by rememberUpdatedState(onConsentDenied)
    val currentOnConsentResult by rememberUpdatedState(onConsentResult)
    val currentOnVpnConflictResolved by rememberUpdatedState(onVpnConflictResolved)
    val currentDecision by rememberUpdatedState(decision)
    var pendingStart by remember { mutableStateOf<WebsiteBlockingRuntimeDecision.Running?>(null) }
    // 다른 VPN 을 끊어도 되는지 사용자가 답한 결과. 잠금이 끝나면 다시 물어본다.
    var displacementApproved by remember { mutableStateOf(false) }
    var pendingDisplacement by remember { mutableStateOf<WebsiteBlockingRuntimeDecision.Running?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val start = pendingStart
        pendingStart = null
        val granted = result.resultCode == Activity.RESULT_OK
        val outcome = WebsiteBlockingConsentResultPolicy.outcome(
            granted = granted,
            hasPendingStart = start != null,
        )
        // Ignore 는 우리가 띄우지 않은 결과라 사용자의 답이 아니다. 그것까지 세면 거부율이
        // 실제보다 커진다.
        if (outcome != WebsiteBlockingConsentOutcome.Ignore) {
            currentOnConsentResult(granted)
        }
        when (outcome) {
            WebsiteBlockingConsentOutcome.StartPending ->
                start?.let { context.startWebsiteBlocking(it.domains, it.stopAtEpochMillis) }

            WebsiteBlockingConsentOutcome.RecordDenied -> {
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.ConsentDenied)
                currentOnConsentDenied()
            }

            WebsiteBlockingConsentOutcome.Ignore -> Unit
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

    /*
     * 위 효과는 판정값이 바뀔 때만 돈다. 창 안에서 서비스만 죽으면 판정은 계속 Running 이라
     * 아무 일도 일어나지 않고, 그 창은 끝날 때까지 열린 채 남는다. 사용자가 돌아온 순간
     * 한 번만 다시 확인한다.
     *
     * 여기서는 동의창을 띄우지 않는다. 동의는 사용자가 잠금을 시작할 때 묻는 것이고,
     * 홈에 올 때마다 시스템 창을 다시 들이밀면 거부가 아니라 괴롭힘이 된다.
     */
    LaunchedEffect(resumeCount) {
        val decisionNow = currentDecision
        val status = WebsiteBlockingRuntimeState.status.value
        val hasConsent = VpnService.prepare(context) == null
        AppLogger.debug(
            DIAGNOSTIC_TAG,
            "resume_reassert count=$resumeCount" +
                " decision=${decisionNow::class.simpleName}" +
                " status=$status" +
                " consent=$hasConsent",
        )
        val running = decisionNow as? WebsiteBlockingRuntimeDecision.Running
            ?: return@LaunchedEffect
        if (!WebsiteBlockingReassertPolicy.shouldReassertOnResume(status)) return@LaunchedEffect
        if (!hasConsent) return@LaunchedEffect
        context.startWebsiteBlocking(running.domains, running.stopAtEpochMillis)
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
                currentOnVpnConflictResolved(true)
            },
            onDismiss = {
                pendingDisplacement = null
                // 잠금은 그대로 두고 웹 차단만 포기한다. 왜 막히지 않는지는 배너가 말한다.
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Unavailable)
                currentOnVpnConflictResolved(false)
            },
        )
    }
}

/**
 * 다른 앱이 VPN 슬롯을 쥐고 있는지. 소유자 UID 는 시스템 권한 없이 읽을 수 없으므로,
 * 우리 서비스가 서 있지 않은데 VPN 이 존재하면 남의 것으로 본다.
 *
 * `allNetworks` 는 API 31 부터 deprecated 지만 아직 그대로 둔다. 대체 경로인 NetworkCallback
 * 은 등록·해제 수명주기를 들고 다녀야 해서 이 자리의 동기 판정으로 바꿔 끼울 수 없고,
 * `activeNetwork` 하나만 보면 VPN 이 활성 네트워크가 아닐 때를 놓친다. 이 판정은 실기기에서
 * 확인한 충돌 처리(`유니콘 HTTPS`, 스파이크 문서의 "PASS AFTER FIX")가 딛고 선 자리라,
 * 기기 재검증 없이 바꾸면 그 결과를 잃는다.
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

private const val DIAGNOSTIC_TAG = "KeepRoutineWeb"
