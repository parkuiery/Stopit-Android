package com.uiery.keep.feature.lockhistory.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.model.LockHistoryModel
import com.uiery.keep.util.formatTwentyFourHourTime

@Composable
internal fun LockHistorySessionItem(
    modifier: Modifier = Modifier,
    session: LockHistoryModel,
) {
    val context = LocalContext.current

    // 카드가 아니라 목록의 한 줄이다.
    //
    // 세션은 같은 모양이 반복되는 기록이고 누를 수 없다. 카드로 감싸면 줄마다 눌러도 되는
    // 객체처럼 보이고, 안쪽 여백만큼 목록이 길어져 훑어보기 어려워진다. 묶음은 날짜 머리글이
    // 이미 나누고 있으므로 면을 따로 둘 이유가 없다.
    //
    // (원래 쓰던 NeutralWeak 는 이 화면 배경과 같은 gray200 이라 면이 아예 그려지지 않았다.
    // 즉 지금까지도 카드로 보인 적이 없고, 여백과 모서리만 비용으로 내고 있었다.)
    Row(
        modifier = modifier
            // 폭을 채우지 않으면 Row 가 내용 크기로 줄어든다. 그러면 나눠 줄 남는 폭이
            // 없어 시간과 지속 시간이 서로 맞붙는다.
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 남는 폭은 왼쪽이 가져간다. 지속 시간은 짧고 먼저 자리를 잡으므로, 시간 범위가
        // 길어져도 지속 시간을 밀어내지 않는다.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatTwentyFourHourTime(session.startDateTime.toLocalTime())} - ${formatTwentyFourHourTime(session.endDateTime.toLocalTime())}",
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (session.isRoutine) {
                    stringResource(R.string.lock_history_routine_session)
                } else {
                    stringResource(R.string.lock_history_manual_session)
                },
                color = KeepTheme.semanticColors.foreground.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = formatSessionDuration(context, session.durationMillis),
            color = KeepTheme.semanticColors.foreground.brand,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

private fun formatSessionDuration(context: Context, millis: Long): String {
    val totalMinutes = millis / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        context.getString(R.string.lock_history_duration_format, hours, minutes)
    } else {
        context.getString(R.string.lock_history_minutes_format, minutes)
    }
}
