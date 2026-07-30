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
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.first_promise_resume_title),
                style = MaterialTheme.typography.titleMedium,
                color = KeepTheme.colors.onSurfaceVariant,
            )
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
            if (state.isRetry) {
                Text(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = stringResource(R.string.first_promise_resume_retry_message),
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        enabled = !state.isBusy,
                        role = Role.Button,
                        onClick = onActivate,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val actionColor = if (state.isBusy) {
                    KeepTheme.colors.surfaceVariant
                } else {
                    KeepTheme.colors.primary
                }
                if (state.isBusy) {
                    KeepCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = actionColor,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    modifier = if (state.isBusy) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier
                    },
                    text = stringResource(
                        if (state.isBusy) R.string.first_promise_resume_waiting
                        else R.string.first_promise_result_enable,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = actionColor,
                )
                if (!state.isBusy) {
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
    }
}
