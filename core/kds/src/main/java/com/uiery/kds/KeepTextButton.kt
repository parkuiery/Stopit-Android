package com.uiery.kds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

enum class KeepTextButtonVariant {
    Neutral,
    Brand,
    Critical,
}

/** Low-emphasis inline or toolbar action using SEED Ghost-button roles. */
@Composable
fun KeepTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: KeepTextButtonVariant = KeepTextButtonVariant.Neutral,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val contentColor = when (variant) {
        KeepTextButtonVariant.Neutral -> KeepTheme.semanticColors.foreground.neutral
        KeepTextButtonVariant.Brand -> KeepTheme.semanticColors.foreground.brand
        KeepTextButtonVariant.Critical -> KeepTheme.semanticColors.foreground.critical
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minHeight = 44.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContentColor = KeepTheme.semanticColors.foreground.disabled,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        interactionSource = interactionSource,
        content = content,
    )
}
