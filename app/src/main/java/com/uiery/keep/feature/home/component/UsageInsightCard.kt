package com.uiery.keep.feature.home.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepCard
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.keep.R
import com.uiery.keep.domain.usageinsight.UsageInsight
import com.uiery.kds.theme.KeepTheme

sealed interface UsageInsightCardUiState {
    data object Hidden : UsageInsightCardUiState
    data object PermissionNeeded : UsageInsightCardUiState
    data class Insight(val insight: UsageInsight, val appLabel: String) : UsageInsightCardUiState
}

@Composable
fun UsageInsightCard(
    state: UsageInsightCardUiState,
    onCtaClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is UsageInsightCardUiState.Hidden) return
    val (title, body, cta) = when (state) {
        is UsageInsightCardUiState.PermissionNeeded -> Triple(
            stringResource(R.string.usage_insight_permission_title),
            stringResource(R.string.usage_insight_permission_body),
            stringResource(R.string.usage_insight_permission_cta),
        )
        is UsageInsightCardUiState.Insight -> when (val insight = state.insight) {
            is UsageInsight.NightOwl -> Triple(
                stringResource(R.string.usage_insight_night_owl_title),
                stringResource(
                    R.string.usage_insight_night_owl_body,
                    state.appLabel,
                    insight.nightsCount,
                    insight.avgNightUsage.toMinutes().toInt(),
                ),
                stringResource(R.string.usage_insight_cta_create_routine),
            )
            is UsageInsight.WeeklySurge -> Triple(
                stringResource(R.string.usage_insight_surge_title),
                stringResource(
                    R.string.usage_insight_surge_body,
                    state.appLabel,
                    surgePercent(insight),
                ),
                stringResource(R.string.usage_insight_cta_create_routine),
            )
        }
        is UsageInsightCardUiState.Hidden -> return
    }
    KeepCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = body,
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                KeepTextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.usage_insight_dismiss),
                        color = KeepTheme.colors.surfaceVariant,
                    )
                }
                KeepTextButton(onClick = onCtaClick) {
                    Text(
                        text = cta,
                        color = KeepTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// Division relies on lastWeekUsage > 0, guaranteed by UsageInsightPolicy (WeeklySurge is only emitted when last week > 0).
private fun surgePercent(insight: UsageInsight.WeeklySurge): Int =
    ((insight.thisWeekUsage.toMillis() - insight.lastWeekUsage.toMillis()) * 100 /
        insight.lastWeekUsage.toMillis()).toInt()
