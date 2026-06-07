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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.lockhistory.LockHistoryPerformanceReportReadModel

@Composable
internal fun LockHistorySummaryCard(
    modifier: Modifier = Modifier,
    totalDuration: Long,
    sessionCount: Int,
    report: LockHistoryPerformanceReportReadModel,
) {
    val context = LocalContext.current
    val durationText = formatDuration(context, totalDuration)
    val headlineText = stringResource(report.headlineResId, durationText)
    val supportingText = stringResource(report.supportingResId, sessionCount)
    val totalDurationLabel = stringResource(R.string.lock_history_total_duration)
    val sessionCountLabel = stringResource(R.string.lock_history_session_count)
    val sessionCountText = stringResource(R.string.lock_history_session_count_value, sessionCount)
    val accessibilityDescription = listOf(
        headlineText,
        supportingText,
        totalDurationLabel,
        durationText,
        sessionCountLabel,
        sessionCountText,
    ).joinToString(separator = ". ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.tertiary)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = headlineText,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = supportingText,
            color = KeepTheme.colors.surface,
            fontSize = 13.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = totalDurationLabel,
                color = KeepTheme.colors.surface,
                fontSize = 14.sp,
            )
            Text(
                text = durationText,
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
                text = sessionCountLabel,
                color = KeepTheme.colors.surface,
                fontSize = 14.sp,
            )
            Text(
                text = sessionCountText,
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
