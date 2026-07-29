package com.uiery.kds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = KeepTheme.semanticColors.background.brandSolid,
            checkmarkColor = KeepTheme.semanticColors.foreground.onBrand,
            uncheckedColor = KeepTheme.semanticColors.stroke.neutralWeak,
            disabledCheckedColor = KeepTheme.semanticColors.background.disabled,
            disabledUncheckedColor = KeepTheme.semanticColors.stroke.neutralMuted,
            disabledIndeterminateColor = KeepTheme.semanticColors.background.disabled,
        ),
        interactionSource = interactionSource,
    )
}
