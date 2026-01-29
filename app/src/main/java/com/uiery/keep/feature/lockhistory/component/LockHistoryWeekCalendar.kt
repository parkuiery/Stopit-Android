package com.uiery.keep.feature.lockhistory.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun LockHistoryWeekCalendar(
    modifier: Modifier = Modifier,
    startDate: LocalDate,
    endDate: LocalDate,
    durationByDate: Map<LocalDate, Long>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val dates = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .toList()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        dates.forEach { date ->
            DayItem(
                modifier = Modifier.weight(1f),
                date = date,
                duration = durationByDate[date] ?: 0L,
                isSelected = date == selectedDate,
                isToday = date == LocalDate.now(),
                onClick = { onSelectDate(date) },
                context = context,
            )
        }
    }
}

@Composable
private fun DayItem(
    modifier: Modifier = Modifier,
    date: LocalDate,
    duration: Long,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    context: Context,
) {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = KeepTheme.colors.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                if (isSelected) KeepTheme.colors.primary.copy(alpha = 0.1f)
                else KeepTheme.colors.tertiary
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = dayOfWeek,
            color = if (isToday) KeepTheme.colors.error else KeepTheme.colors.onTertiaryContainer,
            fontSize = 10.sp,
        )
        Text(
            text = date.dayOfMonth.toString(),
            color = if (isToday) KeepTheme.colors.error else KeepTheme.colors.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
        )
        Text(
            text = formatDurationShort(context, duration),
            color = if (duration > 0) KeepTheme.colors.onSurfaceVariant else KeepTheme.colors.onTertiaryContainer,
            fontSize = 9.sp,
            fontWeight = if (duration > 0) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private fun formatDurationShort(context: Context, millis: Long): String {
    if (millis == 0L) return "-"
    val totalMinutes = millis / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        context.getString(R.string.lock_history_duration_short_hour, hours, minutes)
    } else {
        context.getString(R.string.lock_history_duration_short_min, minutes)
    }
}
