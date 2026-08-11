package com.uiery.kds

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

/**
 * KDS icon-only action.
 *
 * The 48dp minimum target is preserved independently of the glyph size. Callers must provide
 * a localized content description through the icon rendered in [content].
 */
@Composable
fun KeepIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = KeepTheme.semanticColors.foreground.neutral,
            disabledContentColor = KeepTheme.semanticColors.foreground.disabled,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}
