package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepBannerAd
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.KeepSwitch
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import kotlinx.datetime.LocalTime

@Composable
internal fun RoutineListContent(
    modifier: Modifier = Modifier,
    routines: List<RoutineModel>,
    onEnabledChange: (Long, Boolean) -> Unit,
    onDetailClick: (Long) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f),
            contentPadding =
                PaddingValues(
                    horizontal = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(routines) { routine ->
                val isRunning = routine.isRunningNow()
                RoutineItem(
                    name = routine.name,
                    startTime = routine.startTime,
                    isEnabled = routine.isEnabled,
                    isRunning = isRunning,
                    onEnabledChange = { onEnabledChange(routine.id, it) },
                    onClick = { if (!isRunning) onDetailClick(routine.id) },
                )
            }
        }
        KeepBannerAd(
            modifier = Modifier.fillMaxWidth(),
            adUnitId = "ca-app-pub-1537867411423705/7750072748",
        )
    }
}

@Composable
private fun RoutineItem(
    modifier: Modifier = Modifier,
    name: String,
    startTime: LocalTime,
    isEnabled: Boolean,
    isRunning: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isRunning) { onClick() }
                .background(
                    color = KeepTheme.colors.tertiary,
                    shape = RoundedCornerShape(12.dp),
                ).padding(horizontal = 12.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val amPmText = if (startTime.hour < 12) R.string.am else R.string.pm
        val displayHour = if (startTime.hour % 12 == 0) 12 else startTime.hour % 12
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(amPmText),
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = "$displayHour:${startTime.minute}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                if (isRunning) {
                    Text(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KeepTheme.colors.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        text = stringResource(R.string.routine_running_tag),
                        color = KeepTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = name,
                color = KeepTheme.colors.surfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        KeepSwitch(
            checked = isEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
}

private fun RoutineModel.isRunningNow(): Boolean {
    if (!isEnabled) {
        return false
    }

    return isRoutineActiveNow(
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toDayOfWeekList(),
    )
}
