package com.uiery.keep.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.lock.LockTargetKind

@Composable
fun ContentDescription(
    modifier: Modifier = Modifier,
    isKeep: Boolean,
    startTime: Long,
    lockTargetKind: LockTargetKind = LockTargetKind.Apps,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val description = if (isKeep) keepOnMessageRes(lockTargetKind) else R.string.keep_off_message
        // 켜짐은 32sp 타이머, 꺼짐은 18sp 문구라 높이가 다르다. 이 영역은 화면 하단에 고정돼
        // 있어 높이가 변하면 그 위의 토글 위치가 함께 움직인다. 두 상태가 같은 자리를 쓰도록
        // 최소 높이를 잡아 둔다.
        Box(
            modifier = Modifier.heightIn(min = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if(isKeep) {
                TimerContent(startTime = startTime)
            } else {
                Text(
                    text = stringResource(R.string.keep_off_status),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.error,
                )
            }
        }
        Text(
            text = stringResource(description),
            color = KeepTheme.colors.onSurface,
        )
    }
}

@StringRes
internal fun keepOnMessageRes(lockTargetKind: LockTargetKind): Int = when (lockTargetKind) {
    LockTargetKind.Apps -> R.string.keep_on_message
    LockTargetKind.Websites -> R.string.keep_on_message_websites
    LockTargetKind.AppsAndWebsites -> R.string.keep_on_message_apps_and_websites
}