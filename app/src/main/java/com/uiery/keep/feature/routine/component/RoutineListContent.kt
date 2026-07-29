package com.uiery.keep.feature.routine.component

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepBadge
import com.uiery.kds.KeepBadgeTone
import com.uiery.kds.KeepBadgeVariant
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.KeepIconButton
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
    val statusLabel = when (status) {
        RoutineCardStatus.Running -> stringResource(R.string.routine_running_tag)
        RoutineCardStatus.Enabled -> stringResource(R.string.routine_enabled_tag)
        RoutineCardStatus.Disabled -> stringResource(R.string.routine_disabled_tag)
    }
    KeepCard(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth(),
        variant = KeepCardVariant.LayerDefault,
        bordered = true,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
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
                        color = KeepTheme.semanticColors.foreground.muted,
                    )
                    Text(
                        text = formatTwelveHourTime(
                            hour24 = startTime.hour,
                            minute = startTime.minute,
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = KeepTheme.semanticColors.foreground.neutral,
                    )
                    KeepBadge(
                        text = statusLabel,
                        tone = if (status == RoutineCardStatus.Running) {
                            KeepBadgeTone.Brand
                        } else {
                            KeepBadgeTone.Neutral
                        },
                        variant = KeepBadgeVariant.Solid,
                    )
                    if (isLocked && changeLockHours != null) {
                        KeepBadge(
                            text = stringResource(R.string.change_lock_hours, changeLockHours),
                            tone = KeepBadgeTone.Neutral,
                            variant = KeepBadgeVariant.Solid,
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_shield),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                        )
                    }
                }
                Text(
                    text = name,
                    color = KeepTheme.semanticColors.foreground.neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repeatDaysLabel,
                    color = KeepTheme.semanticColors.foreground.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nextRunLabel != null) {
                    Text(
                        text = nextRunLabel,
                        color = KeepTheme.semanticColors.foreground.subtle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KeepIconButton(
                enabled = !isBlocked,
                onClick = onShareClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.cd_share_routine_template),
                    tint = KeepTheme.semanticColors.foreground.muted,
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
