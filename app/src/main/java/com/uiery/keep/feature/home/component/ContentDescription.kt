package com.uiery.keep.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
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
    hasActivePomodoroSession: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 홈의 다른 요소(카테고리 버튼·카드)와 같은 좌우 여백. 문구가 한 줄에 담기지
            // 못하는 순간부터 여백이 없으면 글자가 화면 끝에 닿는다.
            .padding(horizontal = HORIZONTAL_GUTTER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 세션이 도는 동안 "켜서 막아 보세요"라고 권하면, 이미 막고 있는 사실과 어긋난다.
        val description = when {
            isKeep -> keepOnMessageRes(lockTargetKind)
            hasActivePomodoroSession -> R.string.pomodoro_home_active_message
            else -> R.string.keep_off_message
        }
        // 켜짐은 32sp 타이머, 꺼짐은 18sp 문구라 높이가 다르다. 이 영역은 화면 하단에 고정돼
        // 있어 높이가 변하면 그 위의 토글 위치가 함께 움직인다. 두 상태가 같은 자리를 쓰도록
        // 최소 높이를 잡아 둔다.
        Box(
            modifier = Modifier.heightIn(min = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if(isKeep) {
                TimerContent(startTime = startTime)
            } else if (hasActivePomodoroSession) {
                // 집중 세션이 앱을 막고 있는 동안 "꺼짐"이라고 말하면 사용자가 보고 있는 사실과
                // 어긋난다. Keep 토글은 실제로 꺼져 있으므로 타이머가 아니라 세션 상태를 말한다.
                Text(
                    text = stringResource(R.string.pomodoro_phase_focus),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.keep_off_status),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KeepTheme.colors.error,
                )
            }
        }
        // 앱만 잠글 때는 한 줄, 웹사이트가 끼면 두 줄이 된다. 줄 수가 오가면 이 묶음은
        // 화면 바닥에 고정돼 있어 그 위의 토글이 함께 움직인다. 두 줄 자리를 늘 잡아
        // 두고, 넘칠 때는 자르지 않고 더 늘어나게 둔다.
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(description),
            color = KeepTheme.colors.onSurface,
            // 가운데 정렬 화면인데 감싼 둘째 줄만 왼쪽에 붙으면 문구가 기울어 보인다.
            textAlign = TextAlign.Center,
            minLines = 2,
            // 기본 줄바꿈은 폭이 차는 지점에서 끊어 "없어요" 같은 꼬리 한 마디만 둘째
            // 줄에 남긴다. Paragraph 는 문단 전체를 보고 두 줄 길이를 고르게 나눈다.
            style = KeepTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
        )
    }
}

/** 홈 본문이 공유하는 좌우 여백. */
private val HORIZONTAL_GUTTER = 20.dp

@StringRes
internal fun keepOnMessageRes(lockTargetKind: LockTargetKind): Int = when (lockTargetKind) {
    LockTargetKind.Apps -> R.string.keep_on_message
    LockTargetKind.Websites -> R.string.keep_on_message_websites
    LockTargetKind.AppsAndWebsites -> R.string.keep_on_message_apps_and_websites
}