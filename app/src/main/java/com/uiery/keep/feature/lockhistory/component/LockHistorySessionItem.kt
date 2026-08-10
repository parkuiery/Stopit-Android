package com.uiery.keep.feature.lockhistory.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
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

    KeepCard(
        modifier = modifier
            .fillMaxWidth(),
        variant = KeepCardVariant.NeutralWeak,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                // 폭을 채우지 않으면 Row 가 내용 크기로 줄어든다. 그러면 나눠 줄 남는 폭이
                // 없어 SpaceBetween 이 아무 일도 하지 않고, 시간과 지속 시간이 서로 맞붙는다.
                .fillMaxWidth()
                .padding(12.dp),
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
