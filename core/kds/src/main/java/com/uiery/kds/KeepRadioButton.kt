package com.uiery.kds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme

@Composable
fun KeepRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = KeepTheme.semanticColors.background.brandSolid,
            unselectedColor = KeepTheme.semanticColors.stroke.neutralSolid,
            disabledSelectedColor = KeepTheme.semanticColors.foreground.disabled,
            disabledUnselectedColor = KeepTheme.semanticColors.stroke.neutralMuted,
        ),
        interactionSource = interactionSource,
    )
}
