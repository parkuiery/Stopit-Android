package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepSwitch
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.analytics.toMetadata
import com.uiery.keep.feature.routine.RoutineCardStatus
import com.uiery.keep.feature.routine.RoutineListAction
import com.uiery.keep.feature.routine.resolveRoutineEnabledSwitchAction
import com.uiery.keep.feature.routine.toRoutineCardReadModel
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.formatLockEndTime
import com.uiery.keep.util.formatTwelveHourTime
import com.uiery.keep.util.isChangeLocked
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalTime

@Composable
internal fun RoutineListContent(
    modifier: Modifier = Modifier,
    routines: List<RoutineModel>,
    onEnabledChange: (Long, Boolean) -> Unit,
    onDetailClick: (Long) -> Unit,
    onShareClick: (Long) -> Unit,
    onBlockedRoutineAction: () -> Unit,
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
                val readModel = routine.toRoutineCardReadModel()
                val isRunning = readModel.status == RoutineCardStatus.Running
                val isLocked = routine.isChangeLocked()
                val isBlocked = isRunning || isLocked
                RoutineItem(
                    name = routine.name,
                    routineId = routine.id,
                    startTime = routine.startTime,
                    status = readModel.status,
                    repeatDaysLabel = readModel.repeatDays.toRoutineRepeatDaysLabel(),
                    nextRunLabel = readModel.nextRunAt?.let { nextRunAt ->
                        stringResource(
                            R.string.routine_next_run_label,
                            formatLockEndTime(nextRunAt),
                        )
                    },
                    isEnabled = routine.isEnabled,
                    isRunning = isRunning,
                    isLocked = isLocked,
                    changeLockHours = routine.changeLockHours,
                    onEnabledChange = { requestedEnabled ->
                        when (
                            val action = resolveRoutineEnabledSwitchAction(
                                routineId = routine.id,
                                requestedEnabled = requestedEnabled,
                                isBlocked = isBlocked,
                            )
                        ) {
                            RoutineListAction.Blocked -> onBlockedRoutineAction()
                            is RoutineListAction.ToggleEnabled -> onEnabledChange(
                                action.routineId,
                                action.isEnabled,
                            )
                        }
                    },
                    onClick = {
                        if (isBlocked) {
                            onBlockedRoutineAction()
                        } else {
                            onDetailClick(routine.id)
                        }
                    },
                    onShareClick = { onShareClick(routine.id) },
                )
            }
        }
        TrackedBannerAd(
            modifier = Modifier.fillMaxWidth(),
            metadata = AdPlacement.RoutineListBottom.toMetadata(
                screenName = KeepAnalyticsScreen.ROUTINE,
                screenContext = "list",
            ),
        )
    }
}

@Composable
private fun RoutineItem(
    modifier: Modifier = Modifier,
    name: String,
    routineId: Long,
    startTime: LocalTime,
    status: RoutineCardStatus,
    repeatDaysLabel: String,
    nextRunLabel: String?,
    isEnabled: Boolean,
    isRunning: Boolean,
    isLocked: Boolean,
    changeLockHours: Int?,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val isBlocked = isRunning || isLocked
    val cardColor = if (status == RoutineCardStatus.Disabled) {
        KeepTheme.colors.tertiaryContainer
    } else {
        KeepTheme.colors.tertiary
    }
    val statusLabel = when (status) {
        RoutineCardStatus.Running -> stringResource(R.string.routine_running_tag)
        RoutineCardStatus.Enabled -> stringResource(R.string.routine_enabled_tag)
        RoutineCardStatus.Disabled -> stringResource(R.string.routine_disabled_tag)
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(12.dp),
                ).padding(horizontal = 12.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val amPmText = if (startTime.hour < 12) R.string.am else R.string.pm
        Column(
            modifier = Modifier.weight(1f),
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
                RoutineStatusBadge(
                    text = statusLabel,
                    containerColor = if (status == RoutineCardStatus.Running) {
                        KeepTheme.colors.primary
                    } else {
                        KeepTheme.colors.onSurfaceVariant
                    },
                    contentColor = if (status == RoutineCardStatus.Running) {
                        KeepTheme.colors.onPrimary
                    } else {
                        KeepTheme.colors.tertiary
                    },
                )
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = repeatDaysLabel,
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nextRunLabel != null) {
                Text(
                    text = nextRunLabel,
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
            modifier = Modifier.testTag("routine-enabled-switch-$routineId"),
            enabled = true,
            onCheckedChange = onEnabledChange,
        )
    }
}

@Composable
private fun RoutineStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        text = text,
        color = contentColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun List<java.time.DayOfWeek>.toRoutineRepeatDaysLabel(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return if (isEmpty()) {
        stringResource(R.string.routine_repeat_days_empty)
    } else {
        joinToString(" · ") { dayOfWeek ->
            dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
    }
}
