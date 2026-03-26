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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.Picker
import com.uiery.keep.R
import com.uiery.keep.rememberPickerState
import com.uiery.keep.feature.home.CountdownDuration

@Composable
fun CountDownPicker(
    modifier: Modifier = Modifier,
    onChangeCountdownDuration: (CountdownDuration) -> Unit,
) {
    val dayValues = remember { (0..7).map { it.toString() } }
    val hourValues = remember { (0..23).map { it.toString() } }
    val minuteValues = remember { (0..59).map { it.toString() } }
    val dayPickerState = rememberPickerState()
    val hourPickerState = rememberPickerState()
    val minutePickerState = rememberPickerState()

    LaunchedEffect(dayPickerState.selectedItem, hourPickerState.selectedItem, minutePickerState.selectedItem) {
        if (dayPickerState.selectedItem.isNotEmpty() && hourPickerState.selectedItem.isNotEmpty() && minutePickerState.selectedItem.isNotEmpty()) {
            onChangeCountdownDuration(
                CountdownDuration(
                    day = dayPickerState.selectedItem.toInt(),
                    hour = hourPickerState.selectedItem.toInt(),
                    minute = minutePickerState.selectedItem.toInt(),
                )
            )
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
                modifier = Modifier.widthIn(min = 28.dp),
                state = dayPickerState,
                items = dayValues,
                visibleItemsCount = 7,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.day_unit),
                fontWeight = FontWeight.Bold,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Picker(
                modifier = Modifier.widthIn(min = 28.dp),
                state = hourPickerState,
                items = hourValues,
                visibleItemsCount = 7,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.hour),
                fontWeight = FontWeight.Bold,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Picker(
                modifier = Modifier.widthIn(min = 28.dp),
                state = minutePickerState,
                items = minuteValues,
                visibleItemsCount = 7,
                color = KeepTheme.colors.onSurfaceVariant,
                textStyle = TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textModifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.minute),
                fontWeight = FontWeight.Bold,
                color = KeepTheme.colors.onSurfaceVariant,
            )
        }
    }

}