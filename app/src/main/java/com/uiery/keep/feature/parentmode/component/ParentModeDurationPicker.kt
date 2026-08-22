package com.uiery.keep.feature.parentmode.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.Picker
import com.uiery.keep.R
import com.uiery.keep.rememberPickerState

// 부모 모드에는 PIN 분실 복구 경로가 없다 — 만들면 그게 곧 아이의 우회로다. 남은 유일한
// 복구는 만료를 기다리는 것이고, 그래서 이 상한이 곧 최악의 대기 시간이다. 프리셋이 60분에서
// 끝나는 것과 달리 휠은 12시간까지 열려 있었는데, 그건 실사용 범위가 아니라 휠의 부산물이었다.
private const val MAX_PARENT_MODE_HOURS = 4
private val HOUR_VALUES = (0..MAX_PARENT_MODE_HOURS).map(Int::toString)
private val MINUTE_VALUES = (0..59).map(Int::toString)

/**
 * The allowed time is a duration, and the app already asks for durations with a wheel on the manual
 * countdown lock. Parent mode used to ask with preset chips plus a separate number field, which put
 * the same value on screen three times over; the wheel is the one place it lives now, and the
 * presets above it are shortcuts that move this wheel rather than a second copy of the value.
 */
@Composable
internal fun ParentModeDurationPicker(
    hours: Int,
    minutes: Int,
    onDurationChange: (hours: Int, minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requested = hours to minutes
    // What the wheel is currently showing. A preset moves the value out from under the wheel, and
    // the wheel is scroll state rather than a controlled input, so it is remounted to catch up.
    var applied by remember { mutableStateOf(requested) }
    var wheelGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(requested) {
        if (requested != applied) {
            applied = requested
            wheelGeneration++
        }
    }

    val hourPickerState = rememberPickerState()
    val minutePickerState = rememberPickerState()

    LaunchedEffect(hourPickerState.selectedItem, minutePickerState.selectedItem) {
        val dialledHours = hourPickerState.selectedItem.toIntOrNull() ?: return@LaunchedEffect
        val dialledMinutes = minutePickerState.selectedItem.toIntOrNull() ?: return@LaunchedEffect
        applied = dialledHours to dialledMinutes
        if (dialledHours != hours || dialledMinutes != minutes) {
            onDurationChange(dialledHours, dialledMinutes)
        }
    }

    val pickerDescription = stringResource(
        id = R.string.cd_parent_mode_duration_picker,
        hours,
        minutes,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("parent_mode_duration_picker")
            .semantics { contentDescription = pickerDescription },
        contentAlignment = Alignment.Center,
    ) {
        // The band behind the middle row is what marks the value the wheel is resting on.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(40.dp)
                .background(
                    shape = RoundedCornerShape(10.dp),
                    color = KeepTheme.colors.tertiary,
                ),
        ) {}

        key(wheelGeneration) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                DurationWheel(
                    state = hourPickerState,
                    items = HOUR_VALUES,
                    startIndex = hours.coerceIn(0, MAX_PARENT_MODE_HOURS),
                )
                DurationWheelUnit(text = stringResource(id = R.string.hour))
                Spacer(modifier = Modifier.width(16.dp))
                DurationWheel(
                    state = minutePickerState,
                    items = MINUTE_VALUES,
                    startIndex = minutes.coerceIn(0, 59),
                )
                DurationWheelUnit(text = stringResource(id = R.string.minute))
            }
        }
    }
}

@Composable
private fun DurationWheel(
    state: com.uiery.keep.PickerState,
    items: List<String>,
    startIndex: Int,
) {
    Picker(
        modifier = Modifier.widthIn(min = 36.dp),
        state = state,
        items = items,
        startIndex = startIndex,
        // 3행이면 위아래 이웃 값이 하나씩 보여 휠이라는 건 충분히 읽히고, 5행일 때 이 카드가
        // 접힌 화면의 절반을 넘게 먹으며 허용 앱 선택 버튼을 밀어내던 것을 되돌린다.
        visibleItemsCount = 3,
        color = KeepTheme.colors.onSurfaceVariant,
        textStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        ),
        textModifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun DurationWheelUnit(text: String) {
    Text(
        modifier = Modifier.padding(start = 4.dp),
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        // Subordinate to the number it labels, but still readable: onTertiaryContainer resolves to
        // gray500 in light theme, which is the disabled tone rather than a muted text tone.
        color = KeepTheme.colors.onSurface,
    )
}
