package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.util.formatWeekdayShort
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun RoutineDayContent(
    modifier: Modifier = Modifier,
    selectDays: List<DayOfWeek>,
    onSelectDay: (DayOfWeek) -> Unit,
) {
    val appLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    RoutineSettingCard(
        modifier = modifier.fillMaxWidth(),
        topContent = {
            Text(
                modifier = Modifier.padding(vertical = 6.dp),
                text = stringResource(R.string.repeat_days),
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 16.sp,
            )
        },
        bottomContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),
            ) {
                DayOfWeek.entries.forEach { day ->
                    DayButton(
                        modifier = Modifier.weight(1f),
                        text = formatWeekdayShort(day, appLocale),
                        isSelect = selectDays.contains(day),
                        onSelect = { onSelectDay(day) }
                    )
                }
            }
        },
    )
}

@Composable
private fun DayButton(
    modifier: Modifier = Modifier,
    text: String,
    isSelect: Boolean,
    onSelect: () -> Unit,
) {
    val (textColor, backgroundColor) = if (isSelect) Color.White to KeepTheme.colors.primary else KeepTheme.colors.primary to Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onSelect() }
            .border(
                width = 1.dp,
                color = KeepTheme.colors.primary,
                shape = RoundedCornerShape(4.dp),
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp),
            )
            //.padding(6.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}