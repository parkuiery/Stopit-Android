package com.uiery.keep.feature.lockhistory.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(KeepTheme.colors.tertiary)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${formatTwentyFourHourTime(session.startDateTime.toLocalTime())} - ${formatTwentyFourHourTime(session.endDateTime.toLocalTime())}",
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (session.isRoutine) {
                    stringResource(R.string.lock_history_routine_session)
                } else {
                    stringResource(R.string.lock_history_manual_session)
                },
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 12.sp,
            )
        }
        Text(
            text = formatSessionDuration(context, session.durationMillis),
            color = KeepTheme.colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
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
