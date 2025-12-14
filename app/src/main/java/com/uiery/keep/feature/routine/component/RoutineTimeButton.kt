package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Composable
fun RoutineTimeButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = KeepTheme.colors.tertiary,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = 4.dp,
        ),
    ) {
        Text(
            text = text,
            color = KeepTheme.colors.onSurfaceVariant,
        )
    }
}