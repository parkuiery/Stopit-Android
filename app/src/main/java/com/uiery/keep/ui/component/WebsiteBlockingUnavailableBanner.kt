package com.uiery.keep.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 */
@Composable
fun WebsiteBlockingUnavailableBanner(
    hasWebsiteTargets: Boolean,
    modifier: Modifier = Modifier,
) {
    val status by WebsiteBlockingRuntimeState.status.collectAsState()
    val messageRes = websiteBlockingUnavailableMessageRes(
        hasWebsiteTargets = hasWebsiteTargets,
        status = status,
    ) ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(
                color = KeepTheme.colors.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(KeepTheme.colors.error, CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(messageRes),
            color = KeepTheme.colors.error,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

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
        WebsiteBlockingStatus.Active, WebsiteBlockingStatus.Inactive -> null
    }
}
