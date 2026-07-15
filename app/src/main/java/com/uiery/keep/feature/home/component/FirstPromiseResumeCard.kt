package com.uiery.keep.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepButton
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.first_promise_resume_title),
                style = MaterialTheme.typography.titleMedium,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.first_promise_resume_summary,
                    state.appLabel,
                    formatTime(state.startMinutes),
                    state.repeatDays.sorted().joinToString(" · ") {
                        formatWeekdayShort(DayOfWeek.of(it), Locale.getDefault())
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = KeepTheme.colors.surfaceVariant,
            )
            if (state.isRetry) {
                Text(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = stringResource(R.string.first_promise_resume_retry_message),
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    if (state.isBusy) R.string.first_promise_resume_waiting
                    else R.string.first_promise_result_enable,
                ),
                enabled = !state.isBusy,
                onClick = onActivate,
            )
        }
    }
}
