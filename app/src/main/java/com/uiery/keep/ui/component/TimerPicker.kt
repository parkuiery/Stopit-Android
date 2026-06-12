package com.uiery.keep.ui.component

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.Picker
import com.uiery.keep.R
import com.uiery.keep.rememberPickerState
import com.uiery.keep.util.timeNow
import kotlinx.datetime.LocalTime

@Composable
fun TimerPicker(
    modifier: Modifier = Modifier,
    time: LocalTime = timeNow,
    onChangeTimerTime: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val timePeriodsValues = remember { listOf(context.getString(R.string.am),context.getString(R.string.pm)) }
    val hourValues = remember { timerPickerHourLabels() }
    val minuteValues = remember { (0..59).map { it.toString() } }
    val timerPeriodsPickerState = rememberPickerState()
    val hourPickerState = rememberPickerState()
    val minutePickerState = rememberPickerState()
    val currentSelection = timerPickerSelection(time)
    var pendingExternalSelection by remember { mutableStateOf<TimerPickerSelection?>(currentSelection) }

    LaunchedEffect(time) {
        pendingExternalSelection = timerPickerSelection(time)
    }

    LaunchedEffect(timerPeriodsPickerState.selectedItem,hourPickerState.selectedItem,minutePickerState.selectedItem,time) {
        val pickerSelection = timerPickerSelectionOrNull(
            hasPeriodSelection = timerPeriodsPickerState.selectedItem.isNotEmpty(),
            isPm = timerPeriodsPickerState.selectedItem == context.getString(R.string.pm),
            hourLabel = hourPickerState.selectedItem,
            minute = minutePickerState.selectedItem.toIntOrNull(),
        ) ?: return@LaunchedEffect

        val externalSelection = pendingExternalSelection
        if (externalSelection != null) {
            if (pickerSelection == externalSelection) {
                pendingExternalSelection = null
            }
            return@LaunchedEffect
        }

        val selectedTime = timerPickerChangedTimeOrNull(
            externalTime = time,
            pickerSelection = pickerSelection,
        )
        if (selectedTime != null) {
            onChangeTimerTime(selectedTime)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(32.dp)
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = KeepTheme.colors.tertiary,
                )
        ) {

        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Picker(
                state = timerPeriodsPickerState,
                items = timePeriodsValues,
                startIndex = if(currentSelection.isPm) 1 else 0,
                visibleItemsCount = 3,
                isInfinity = true,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Picker(
                modifier = Modifier.widthIn(min = 28.dp),
                state = hourPickerState,
                items = hourValues,
                startIndex = hourValues.indexOf(currentSelection.hourLabel),
                visibleItemsCount = 7,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(24.dp))
            Picker(
                modifier = Modifier.widthIn(min = 28.dp),
                state = minutePickerState,
                items = minuteValues,
                startIndex = currentSelection.minuteLabel.toInt(),
                visibleItemsCount = 7,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

internal fun timerPickerHourLabels(): List<String> =
    listOf("12") + (1..11).map { it.toString() }

internal data class TimerPickerSelection(
    val isPm: Boolean,
    val hourLabel: String,
    val minuteLabel: String,
)

internal fun timerPickerSelection(time: LocalTime): TimerPickerSelection {
    val hourLabels = timerPickerHourLabels()
    return TimerPickerSelection(
        isPm = time.hour >= HOURS_PER_PERIOD,
        hourLabel = hourLabels[timerPickerStartIndex(time)],
        minuteLabel = time.minute.toString(),
    )
}

internal fun timerPickerStartIndex(time: LocalTime): Int = time.hour % HOURS_PER_PERIOD

internal fun timerPickerSelectedTime(
    isPm: Boolean,
    hourLabel: String,
    minute: Int,
): LocalTime {
    val twelveHour = hourLabel.toInt()
    val baseHour = if (twelveHour == HOURS_PER_PERIOD) 0 else twelveHour
    val hour = if (isPm) baseHour + HOURS_PER_PERIOD else baseHour

    return LocalTime(hour = hour, minute = minute)
}

internal fun timerPickerSelectionOrNull(
    hasPeriodSelection: Boolean = true,
    isPm: Boolean,
    hourLabel: String,
    minute: Int?,
): TimerPickerSelection? {
    if (!hasPeriodSelection || hourLabel.isEmpty() || minute == null) return null
    return TimerPickerSelection(
        isPm = isPm,
        hourLabel = hourLabel,
        minuteLabel = minute.toString(),
    )
}

internal fun timerPickerChangedTimeOrNull(
    externalTime: LocalTime,
    pickerSelection: TimerPickerSelection,
): LocalTime? {
    val selectedTime = timerPickerSelectedTime(
        isPm = pickerSelection.isPm,
        hourLabel = pickerSelection.hourLabel,
        minute = pickerSelection.minuteLabel.toInt(),
    )
    return selectedTime.takeUnless { it == externalTime }
}

private const val HOURS_PER_PERIOD = 12
