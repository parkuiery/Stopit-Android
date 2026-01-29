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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
internal fun LockHistorySummaryCard(
    modifier: Modifier = Modifier,
    totalDuration: Long,
    sessionCount: Int,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.tertiary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.lock_history_total_duration),
                color = KeepTheme.colors.surface,
                fontSize = 14.sp,
            )
            Text(
                text = formatDuration(context, totalDuration),
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.lock_history_session_count),
                color = KeepTheme.colors.surface,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(R.string.lock_history_session_count_value, sessionCount),
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun formatDuration(context: Context, millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return context.getString(R.string.lock_history_duration_format, hours, minutes)
}
