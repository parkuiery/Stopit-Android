package com.uiery.kds

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = KeepTheme.semanticColors.stroke.neutralWeak,
    )
}
