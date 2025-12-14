package com.uiery.keep.feature.home.component

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
import androidx.compose.runtime.remember
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
import kotlinx.datetime.toJavaLocalTime

@Composable
fun TimerPicker(
    modifier: Modifier = Modifier,
    time: LocalTime = timeNow,
    onChangeTimerTime: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val timePeriodsValues = remember { listOf(context.getString(R.string.am),context.getString(R.string.pm)) }
    val hourValues = remember { (0..11).map { it.toString() } }
    val minuteValues = remember { (0..59).map { it.toString() } }
    val timerPeriodsPickerState = rememberPickerState()
    val hourPickerState = rememberPickerState()
    val minutePickerState = rememberPickerState()

    LaunchedEffect(timerPeriodsPickerState.selectedItem,hourPickerState.selectedItem,minutePickerState.selectedItem) {
        if(hourPickerState.selectedItem.isNotEmpty() && minutePickerState.selectedItem.isNotEmpty()) {
            val hour = if(timerPeriodsPickerState.selectedItem == context.getString(R.string.pm)) hourPickerState.selectedItem.toInt() + 12 else hourPickerState.selectedItem.toInt()
            onChangeTimerTime(LocalTime(hour,minutePickerState.selectedItem.toInt()))
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
                startIndex = if(time.toJavaLocalTime().isBefore(java.time.LocalTime.NOON)) 0 else 1,
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
                startIndex = time.hour % 12,
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
                startIndex = time.minute,
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