package com.uiery.keep.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.websiteblocking.WebsiteBlockingRuntimeState
import com.uiery.keep.websiteblocking.WebsiteBlockingStatus

/**
 * 긴급 해제로는 앱만 열린다는 사실을 앱 선택 단계에서 미리 말한다.
 *
 * 긴급 해제는 패키지 단위로 면제를 주는 기능이라 [com.uiery.keep.service.KeepAccessibilityService]
 * 만 참조하고, 웹 차단 판정([com.uiery.keep.domain.websiteblocking.WebsiteBlockingRuntimePolicy])
 * 은 긴급 해제를 아예 입력으로 받지 않는다. 의도된 설계다. "유튜브 앱을 잠깐 연다"가
 * "브라우저로 유튜브를 본다"까지 열어 준다면 긴급 해제는 잠금 전체를 무력화하는 우회로가 된다.
 *
 * 다만 그 설계를 사용자가 알 방법이 없으면, 긴급 해제를 쓴 뒤 브라우저가 여전히 막히는 것을
 * 고장으로 읽는다. 그래서 해제를 요청하기 **전에** 말한다.
 */
@Composable
fun WebsiteBlockingEmergencyUnlockNotice(modifier: Modifier = Modifier) {
    val status by WebsiteBlockingRuntimeState.status.collectAsState()
    if (!websiteBlockingEmergencyUnlockNoticeVisible(status)) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = KeepTheme.colors.onSurfaceVariant.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.emergency_unlock_websites_stay_blocked),
            color = KeepTheme.colors.onSurfaceVariant,
            style = KeepTheme.typography.bodyMedium,
        )
    }
}

/**
 * 웹 차단이 **실제로 서 있을 때만** 알린다.
 *
 * 동의 거부나 다른 VPN 충돌로 필터가 서지 못한 상태에서는 [WebsiteBlockingUnavailableBanner]
 * 가 이미 "웹사이트는 차단되고 있지 않다"고 말하고 있다. 그 옆에서 "웹사이트는 계속 차단된다"고
 * 하면 두 문장이 정면으로 부딪힌다. 앱만 잠근 사용자에게는 애초에 할 말이 아니다.
 */
internal fun websiteBlockingEmergencyUnlockNoticeVisible(status: WebsiteBlockingStatus): Boolean =
    status == WebsiteBlockingStatus.Active
