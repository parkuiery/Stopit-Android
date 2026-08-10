package com.uiery.keep.ui.component

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepTextButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.websiteblocking.WebsiteBlockingRuntimeState
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus

/**
 * 잠금은 걸려 있는데 웹 차단만 서지 못한 경우를 잠금이 끝날 때까지 계속 알린다.
 *
 * 한 번 지나가는 스낵바로 끝내면, 잠금 도중에 상태가 바뀌었을 때(다른 VPN 이 켜지거나
 * 권한이 회수되었을 때) 사용자는 막히고 있다고 믿은 채로 남는다. 수동 잠금은 홈에,
 * 시간 잠금은 잠금 화면에 머무르므로 두 화면 모두에서 같은 사실을 말해야 한다.
 *
 * [onConsentGranted] 를 넘긴 화면에서는 그 자리에서 권한을 다시 받을 수 있다. 자동 경로는
 * 한 번 거부한 잠금에 동의창을 다시 띄우지 않으므로, 도는 잠금을 되살릴 길은 이 버튼뿐이다.
 * 다만 권한을 받는 것만으로 필터가 서지는 않는다. 실제로 세우는 일은 판정을 쥔 화면의
 * 몫이라 [onConsentGranted] 로 넘긴다. 그 처리가 없는 화면은 버튼을 붙이지 않는다.
 * 배너만 사라지고 차단은 서지 않으면, 아무 말도 하지 않는 것보다 나쁘다.
 */
@Composable
fun WebsiteBlockingUnavailableBanner(
    hasWebsiteTargets: Boolean,
    modifier: Modifier = Modifier,
    onConsentGranted: (() -> Unit)? = null,
) {
    val status by WebsiteBlockingRuntimeState.status.collectAsState()
    val messageRes = websiteBlockingUnavailableMessageRes(
        hasWebsiteTargets = hasWebsiteTargets,
        status = status,
    ) ?: return

    WebsiteBlockingWarningRow(
        message = stringResource(messageRes),
        modifier = modifier.padding(top = 16.dp),
        action = if (onConsentGranted != null && websiteBlockingRecoverableInPlace(status)) {
            { WebsiteBlockingConsentRetryButton(onConsentGranted = onConsentGranted) }
        } else {
            null
        },
    )
}

/**
 * 경고가 뜰지를 그리기 전에 알려 준다.
 *
 * 홈은 상단에 한 덩어리만 놓을 수 있다. 경고가 그 자리를 쓸 것인지 미리 알아야 제안 카드를
 * 함께 올릴지 말지 정할 수 있다. 그리면서 판단하면 이미 늦다.
 */
@Composable
internal fun currentWebsiteBlockingWarning(hasWebsiteTargets: Boolean): Int? {
    val status by WebsiteBlockingRuntimeState.status.collectAsState()
    return websiteBlockingUnavailableMessageRes(
        hasWebsiteTargets = hasWebsiteTargets,
        status = status,
    )
}

/**
 * 거부했던 시스템 VPN 동의를 그 자리에서 다시 받는다.
 *
 * 이미 권한이 있는데 상태만 어긋난 경우(설정에서 직접 허용하고 돌아온 경우)에는 동의창이
 * 뜨지 않으므로 곧바로 성공으로 친다.
 */
@Composable
internal fun WebsiteBlockingConsentRetryButton(onConsentGranted: () -> Unit) {
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
            onConsentGranted()
        }
    }

    // 경고 자체는 붉은 톤이지만 이 버튼은 되돌리는 행동이지 파괴적인 행동이 아니다.
    KeepTextButton(
        onClick = {
            val consentIntent = VpnService.prepare(context)
            if (consentIntent == null) {
                WebsiteBlockingRuntimeState.update(WebsiteBlockingStatus.Inactive)
                onConsentGranted()
            } else {
                consentLauncher.launch(consentIntent)
            }
        },
    ) {
        Text(text = stringResource(R.string.website_blocking_consent_retry))
    }
}

/**
 * 웹 차단이 서지 못했다는 경고의 공통 생김새. 화면마다 다른 모양으로 말하면 같은 사실인지
 * 알아보기 어렵다. [action] 은 그 자리에서 바로 복구할 수 있을 때만 붙인다.
 */
@Composable
internal fun WebsiteBlockingWarningRow(
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = KeepTheme.colors.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp)
            // 액션이 붙으면 버튼이 자기 여백(최소 44dp 터치 영역)을 들고 오므로 아래를 줄인다.
            .padding(top = 14.dp, bottom = if (action == null) 14.dp else 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    // 문구가 여러 줄이 되면 가운데 정렬한 점은 두 줄 사이에 떠 버린다.
                    // 첫 줄 높이(19sp)의 가운데에 맞춰 문장이 시작하는 곳을 가리킨다.
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(KeepTheme.colors.error, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                modifier = Modifier
                    .weight(1f)
                    // 잠금 도중에 조건이 바뀌면 이 경고는 소리 없이 나타난다. 화면을 보지
                    // 않는 사용자도 그 순간 알아야 한다.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                text = message,
                color = KeepTheme.colors.error,
                style = KeepTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        if (action != null) {
            // 액션은 문구 옆이 아니라 아래 끝에 둔다(Material Banner). 옆에 끼우면 문구 폭이
            // 버튼만큼 줄어 줄 수가 늘고, 좁은 화면에서는 글자와 버튼이 서로를 밀어낸다.
            Box(modifier = Modifier.align(Alignment.End)) {
                action()
            }
        }
    }
}

/**
 * 이 자리에서 되돌릴 수 있는 고장인지.
 *
 * 동의 거부는 사용자가 지금 여기서 다시 허용할 수 있다. 다른 VPN 과의 충돌은 남의 연결을
 * 끊는 일이라 배너 버튼 하나로 처리할 일이 아니고, 잠금이 시작될 때 확인 대화상자로 따로
 * 묻는다. 네트워크 문제는 사용자가 할 일이 없이 스스로 돌아온다.
 *
 * 되돌릴 수 없는 자리에 버튼을 두면 눌러도 아무 일이 없는 버튼이 된다.
 */
internal fun websiteBlockingRecoverableInPlace(status: WebsiteBlockingStatus): Boolean =
    status == WebsiteBlockingStatus.ConsentDenied

@StringRes
internal fun websiteBlockingUnavailableMessageRes(
    hasWebsiteTargets: Boolean,
    status: WebsiteBlockingStatus,
): Int? {
    // 앱만 잠근 사용자에게 웹 차단 실패를 알리면 무슨 말인지 알 수 없다.
    if (!hasWebsiteTargets) return null
    return when (status) {
        WebsiteBlockingStatus.ConsentDenied -> R.string.website_blocking_unavailable_consent
        WebsiteBlockingStatus.Unavailable -> R.string.website_blocking_unavailable_conflict
        WebsiteBlockingStatus.NetworkUnavailable -> R.string.website_blocking_unavailable_network
        WebsiteBlockingStatus.Active, WebsiteBlockingStatus.Inactive -> null
    }
}
