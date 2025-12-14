package com.uiery.keep.feature.home.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = KeepSwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    thumbContent = thumbContent,
    enabled = enabled,
    colors = colors,
    interactionSource = interactionSource,
)

object KeepSwitchDefaults {
    @Composable
    fun colors(
        checkedThumbColor: Color = Color.White,
        checkedTrackColor: Color = KeepTheme.colors.primary,
        checkedBorderColor: Color = Color.Transparent,
        checkedIconColor: Color = KeepTheme.colors.primary,
        uncheckedThumbColor: Color = Color.White,
        uncheckedTrackColor: Color = KeepTheme.colors.onTertiary,
        uncheckedBorderColor: Color = Color.Transparent,
        uncheckedIconColor: Color = KeepTheme.colors.onTertiary,
    ): SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        checkedBorderColor = checkedBorderColor,
        checkedIconColor = checkedIconColor,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
        uncheckedBorderColor = uncheckedBorderColor,
        uncheckedIconColor = uncheckedIconColor,
    )
}