package com.uiery.keep.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.FirstPromiseResumeCardState
import com.uiery.keep.feature.onboarding.proposal.formatTime
import com.uiery.keep.util.formatWeekdayShort
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun FirstPromiseResumeCard(
    state: FirstPromiseResumeCardState?,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    val formattedDays = state.repeatDays.sorted().joinToString(" · ") {
        formatWeekdayShort(DayOfWeek.of(it), Locale.getDefault())
    }
    val formattedTime = formatTime(state.startMinutes)
    val fullSummary = stringResource(
        R.string.first_promise_resume_summary,
        state.appLabel,
        formattedTime,
        formattedDays,
    )
    KeepCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 액션을 제목 줄로 올려 자기 줄(최소 48dp)과 그 위 간격을 통째로 없앤다. 요약 두
            // 줄은 폭을 그대로 쓰므로 요일이 잘리지 않는다. 제목이 짧아 같은 줄을 나눠도
            // 서로를 밀어내지 않는다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    text = stringResource(R.string.first_promise_resume_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                ResumeAction(state = state, onActivate = onActivate)
            }
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = fullSummary
                },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = KeepTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.first_promise_resume_schedule_summary,
                        formattedTime,
                        formattedDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KeepTheme.colors.surfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 상태를 말하는 줄은 하나뿐이다. 기다리는 중이라는 사실이 먼저이고, 재시도
            // 안내는 그 뒤에 온다. 대기 문구를 액션 자리에 두면 로케일에 따라 제목을 밀어
            // 제목이 여러 줄로 접히므로, 긴 문장은 늘 이 줄에서 말한다.
            val statusMessage = when {
                state.isBusy -> stringResource(R.string.first_promise_resume_waiting)
                state.isRetry -> stringResource(R.string.first_promise_resume_retry_message)
                else -> null
            }
            if (statusMessage != null) {
                Text(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
        }
    }
}

/**
 * 카드가 요구하는 단 하나의 행동. 폭을 채우지 않고 자기 크기만 쓰지만, 터치 영역은
 * 손가락이 닿는 최소치(48dp)를 지킨다.
 *
 * 기다리는 동안에는 회전자만 남긴다. 대기 문구는 로케일에 따라 길어져 제목을 밀어내므로
 * 아래 상태 줄이 맡고, 여기서는 화면을 못 보는 사용자를 위해 이름으로만 남는다.
 */
@Composable
private fun ResumeAction(
    state: FirstPromiseResumeCardState,
    onActivate: () -> Unit,
) {
    val actionColor = if (state.isBusy) {
        KeepTheme.colors.surfaceVariant
    } else {
        KeepTheme.colors.primary
    }
    val waitingLabel = stringResource(R.string.first_promise_resume_waiting)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = !state.isBusy,
                role = Role.Button,
                onClick = onActivate,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isBusy) {
            KeepCircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .semantics { contentDescription = waitingLabel },
                color = actionColor,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.first_promise_result_enable),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = actionColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.round_arrow_forward_ios_24),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = actionColor,
            )
        }
    }
}
