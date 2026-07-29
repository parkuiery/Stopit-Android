package com.uiery.kds

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepDatePickerDialog(
    state: DatePickerState,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        colors = DatePickerDefaults.colors(
            containerColor = KeepTheme.semanticColors.background.layerFloating,
        ),
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepTimeInput(
    state: TimePickerState,
    modifier: Modifier = Modifier,
) {
    TimeInput(
        state = state,
        modifier = modifier,
    )
}
