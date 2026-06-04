package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
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
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.AdPlacementMetadata
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.feature.home.component.KeepSwitch
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.formatTwelveHourTime
import com.uiery.keep.util.isChangeLocked
import com.uiery.keep.util.isRunningNow
import kotlinx.datetime.LocalTime

@Composable
internal fun RoutineListContent(
    modifier: Modifier = Modifier,
    routines: List<RoutineModel>,
    onEnabledChange: (Long, Boolean) -> Unit,
    onDetailClick: (Long) -> Unit,
    onShareClick: (Long) -> Unit,
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
                val isLocked = routine.isChangeLocked()
                val isBlocked = isRunning || isLocked
                RoutineItem(
                    name = routine.name,
                    startTime = routine.startTime,
                    isEnabled = routine.isEnabled,
                    isRunning = isRunning,
                    isLocked = isLocked,
                    changeLockHours = routine.changeLockHours,
                    onEnabledChange = { if (!isBlocked) onEnabledChange(routine.id, it) },
                    onClick = { if (!isBlocked) onDetailClick(routine.id) },
                    onShareClick = { onShareClick(routine.id) },
                )
            }
        }
        TrackedBannerAd(
            modifier = Modifier.fillMaxWidth(),
            metadata = AdPlacementMetadata(
                screenName = KeepAnalyticsScreen.ROUTINE,
                screenContext = "list",
                placement = AdPlacement.RoutineListBottom.analyticsPlacement,
                adUnitId = AdPlacement.RoutineListBottom.adUnitId,
            ),
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
    isLocked: Boolean,
    changeLockHours: Int?,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val isBlocked = isRunning || isLocked
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !isBlocked) { onClick() }
                .background(
                    color = KeepTheme.colors.tertiary,
                    shape = RoundedCornerShape(12.dp),
                ).padding(horizontal = 12.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val amPmText = if (startTime.hour < 12) R.string.am else R.string.pm
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
                    text = formatTwelveHourTime(
                        hour24 = startTime.hour,
                        minute = startTime.minute,
                    ),
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
                if (isLocked && changeLockHours != null) {
                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KeepTheme.colors.onSurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = KeepTheme.colors.tertiary,
                        )
                        Text(
                            text = stringResource(R.string.change_lock_hours, changeLockHours),
                            color = KeepTheme.colors.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                text = name,
                color = KeepTheme.colors.surfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            enabled = !isBlocked,
            onClick = onShareClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.cd_share_routine_template),
                tint = if (isBlocked) KeepTheme.colors.surfaceVariant else KeepTheme.colors.primary,
            )
        }
        KeepSwitch(
            checked = isEnabled,
            enabled = !isBlocked,
            onCheckedChange = onEnabledChange,
        )
    }
}
