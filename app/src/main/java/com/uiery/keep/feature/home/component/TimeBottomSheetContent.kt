package com.uiery.keep.feature.home.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepDivider
import com.uiery.kds.KeepSegmentedControl
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import com.uiery.kds.KeepTextButton
import com.uiery.kds.KeepTextButtonVariant
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.CountdownDuration
import com.uiery.keep.ui.component.TimerPicker
import com.uiery.keep.util.timeNow
import kotlinx.datetime.LocalTime

private const val POMODORO_SEGMENT_INDEX = 2

@Composable
fun TimeBottomSheetContent(
    modifier: Modifier = Modifier,
    blockTime: LocalTime,
    countdownDays: Int = 0,
    countdownTime: LocalTime = LocalTime(0, 0),
    onChangeCountdownDuration: (CountdownDuration) -> Unit,
    onChangeTimerTIme: (LocalTime) -> Unit,
    pomodoroFocusMinutes: Int = 0,
    pomodoroTotalMinutes: Int = 0,
    hasUsedPomodoro: Boolean = false,
    onLockClick: () -> Unit,
    onPomodoroClick: () -> Unit,
    onPomodoroSettingsClick: () -> Unit = onPomodoroClick,
) {
    // 이 카테고리의 기본 요건이 "1–2탭 안에 시작"이다. 전에 사이클을 써 본 사용자는 시트가
    // 열리자마자 시작 버튼 앞에 서 있어야 한다.
    var selectedIndex by remember {
        mutableIntStateOf(if (hasUsedPomodoro) POMODORO_SEGMENT_INDEX else 0)
    }

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
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = KeepTheme.semanticColors.background.neutralWeak,
                ),
        ) {
            // 사이클은 여기서 만료 시각을 고르지 않는다. 남겨 두면 세션과 무관한 시각이
            // 이 세션의 종료 시간인 것처럼 읽힌다.
            if (selectedIndex != POMODORO_SEGMENT_INDEX) {
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
            KeepDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                thickness = 1.dp,
            )
            }
            KeepSegmentedControl(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 68.dp),
                // 집중 세션도 "앱이 언제까지 막히는가"를 정하는 일이다. 별도 진입점을 두면
                // 사용자는 잠금에 대한 모델을 두 개 갖게 된다. 여기가 그 질문을 하는 자리다.
                items = listOf(
                    stringResource(R.string.countdown),
                    stringResource(R.string.timer),
                    stringResource(R.string.pomodoro_sheet_segment),
                ),
                selectedIndex = selectedIndex,
                onItemSelected = { selectedIndex = it },
            )
            Crossfade(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                targetState = selectedIndex
            ) {
                when(it) {
                    0 -> CountDownPicker(onChangeCountdownDuration = onChangeCountdownDuration)
                    1 -> TimerPicker(
                        time = blockTime,
                        onChangeTimerTime = onChangeTimerTIme,
                    )
                    // 사이클은 길이 하나가 아니라 시간표를 고르는 일이라 여기서 다 담지 않는다.
                    // 2시간이 넘는 잠금을 거는 결정이므로 좁은 시트가 아니라 제 화면에서 한다.
                    2 -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 마지막으로 쓰던 사이클과 그 세션이 실제로 잠그는 전체 시간.
                        val hours = pomodoroTotalMinutes / 60
                        val minutes = pomodoroTotalMinutes % 60
                        Text(
                            text = if (hours > 0) {
                                stringResource(
                                    R.string.pomodoro_sheet_summary_hours,
                                    pomodoroFocusMinutes,
                                    hours,
                                    minutes,
                                )
                            } else {
                                stringResource(
                                    R.string.pomodoro_sheet_summary_minutes,
                                    pomodoroFocusMinutes,
                                    pomodoroTotalMinutes,
                                )
                            },
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.pomodoro_sheet_description),
                            color = KeepTheme.colors.surfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        KeepTextButton(
                            onClick = onPomodoroSettingsClick,
                            variant = KeepTextButtonVariant.Brand,
                        ) {
                            Text(text = stringResource(R.string.pomodoro_sheet_change))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (selectedIndex != POMODORO_SEGMENT_INDEX) {
            Text(
                text = stringResource(R.string.lock_message),
                color = KeepTheme.colors.surface,
            )
        }
        val timerDuration = calculateTimerDuration(now = timeNow, target = blockTime)
        val hour = if (selectedIndex == 0) {
            countdownTime.hour
        } else {
            timerDuration.hours
        }
        val minute = if (selectedIndex == 0) {
            countdownTime.minute
        } else {
            timerDuration.minutes
        }
        val lockButtonLabel = if (countdownDays > 0) {
            stringResource(R.string.lock_duration_with_day, countdownDays, hour, minute)
        } else {
            stringResource(R.string.lock_duration, hour, minute)
        }
        KeepButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            text = if (selectedIndex == POMODORO_SEGMENT_INDEX) {
                stringResource(R.string.pomodoro_setup_start)
            } else {
                lockButtonLabel
            },
            enabled = selectedIndex == POMODORO_SEGMENT_INDEX ||
                countdownDays > 0 || hour != 0 || minute != 0,
            onClick = if (selectedIndex == POMODORO_SEGMENT_INDEX) onPomodoroClick else onLockClick,
        )
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
        onPomodoroClick = {},
    )
}
