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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
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
    KeepCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        variant = KeepCardVariant.NeutralWeak,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(
            text = headlineText,
            color = KeepTheme.semanticColors.foreground.neutral,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = supportingText,
            color = KeepTheme.semanticColors.foreground.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = totalDurationLabel,
                color = KeepTheme.semanticColors.foreground.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = durationText,
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = sessionCountLabel,
                color = KeepTheme.semanticColors.foreground.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = sessionCountText,
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        }
    }
}

private fun formatDuration(context: Context, millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return context.getString(R.string.lock_history_duration_format, hours, minutes)
}
