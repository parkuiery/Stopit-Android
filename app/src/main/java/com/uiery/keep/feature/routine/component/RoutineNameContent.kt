package com.uiery.keep.feature.routine.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun RoutineNameContent(
    modifier: Modifier = Modifier,
    name: String,
    setName: (String) -> Unit,
) {
    TextField(
        modifier = modifier.fillMaxWidth(),
        value = name,
        onValueChange = setName,
        placeholder = {
            Text(
                text = stringResource(R.string.routine_name_placeholder),
                color = KeepTheme.colors.onTertiary,
            )
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = KeepTheme.colors.secondary,
            focusedContainerColor = KeepTheme.colors.secondary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = KeepTheme.colors.onSurfaceVariant,
            unfocusedTextColor = KeepTheme.colors.onSurfaceVariant,
            cursorColor = KeepTheme.colors.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}