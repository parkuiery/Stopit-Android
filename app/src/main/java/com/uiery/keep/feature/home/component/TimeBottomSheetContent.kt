package com.uiery.keep.feature.home.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.CountdownDuration
import com.uiery.keep.util.timeNow
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime

@Composable
fun TimeBottomSheetContent(
    modifier: Modifier = Modifier,
    blockTime: LocalTime,
    countdownDays: Int = 0,
    countdownTime: LocalTime = LocalTime(0, 0),
    onChangeCountdownDuration: (CountdownDuration) -> Unit,
    onChangeTimerTIme: (LocalTime) -> Unit,
    onLockClick: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp)
                .background(shape = RoundedCornerShape(8.dp), color = KeepTheme.colors.secondary),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.end_time),
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (countdownDays > 0) {
                    val targetDate = java.time.LocalDate.now().plusDays(
                        countdownDays.toLong() + if (timeNow > blockTime) 1L else 0L
                    )
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(R.string.lock_time_with_date, targetDate.monthValue, targetDate.dayOfMonth, blockTime.hour, blockTime.minute),
                        color = KeepTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    if (timeNow > blockTime) {
                        Text(
                            modifier = Modifier.padding(end = 4.dp),
                            text = stringResource(R.string.next_day),
                            color = KeepTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.lock_time, blockTime.hour, blockTime.minute),
                        color = KeepTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                thickness = 1.dp,
                color = KeepTheme.colors.tertiary,
            )
            SegmentedControl(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 68.dp),
                items = listOf(stringResource(R.string.countdown), stringResource(R.string.timer)),
                onItemSelection = { selectedIndex = it },
            )
            Crossfade(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                targetState = selectedIndex
            ) {
                when(it) {
                    0 -> CountDownPicker(onChangeCountdownDuration = onChangeCountdownDuration)
                    1 -> TimerPicker(onChangeTimerTime = onChangeTimerTIme)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.lock_message),
            color = KeepTheme.colors.surface,
        )
        val hour = if (selectedIndex == 0) {
            countdownTime.hour
        } else {
            blockTime.toJavaLocalTime().minusHours(timeNow.hour.toLong()).hour
        }
        val minute = if (selectedIndex == 0) {
            countdownTime.minute
        } else {
            blockTime.toJavaLocalTime().minusMinutes(timeNow.minute.toLong()).minute
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            enabled = countdownDays > 0 || hour != 0 || minute != 0,
            colors = ButtonColors(
                containerColor = KeepTheme.colors.primary,
                contentColor = Color.White,
                disabledContainerColor = KeepTheme.colors.tertiary,
                disabledContentColor = Color.White,
            ),
            onClick = onLockClick,
        ) {
            Text(
                text = if (countdownDays > 0) {
                    stringResource(R.string.lock_duration_with_day, countdownDays, hour, minute)
                } else {
                    stringResource(R.string.lock_duration, hour, minute)
                },
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            )
        }
    }
}

@Preview
@Composable
private fun TimeBottomSheetContentPreview() {
    TimeBottomSheetContent(
        blockTime = timeNow,
        countdownDays = 0,
        countdownTime = LocalTime(0, 0),
        onChangeCountdownDuration = {},
        onChangeTimerTIme = {},
        onLockClick = {},
    )
}