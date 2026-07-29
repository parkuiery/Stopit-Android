package com.uiery.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

enum class KeepMenuItemTone {
    Neutral,
    Critical,
}

@Immutable
internal data class KeepMenuDimensions(
    val width: Dp,
    val cornerRadius: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val contentGap: Dp,
)

internal fun keepMenuDimensions(): KeepMenuDimensions =
    KeepMenuDimensions(
        width = 240.dp,
        cornerRadius = 16.dp,
        horizontalPadding = 16.dp,
        verticalPadding = 12.dp,
        contentGap = 12.dp,
    )

@Composable
fun KeepMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimensions = keepMenuDimensions()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.width(dimensions.width),
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadius),
        containerColor = KeepTheme.semanticColors.background.layerFloating,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}

@Composable
fun KeepMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: KeepMenuItemTone = KeepMenuItemTone.Neutral,
    description: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val dimensions = keepMenuDimensions()
    val colors = KeepTheme.semanticColors
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = when {
        !enabled -> colors.foreground.disabled
        tone == KeepMenuItemTone.Critical -> colors.foreground.critical
        else -> colors.foreground.neutral
    }
    val containerColor by animateColorAsState(
        targetValue = if (isPressed) {
            colors.background.layerFloatingPressed
        } else {
            colors.background.transparent
        },
        label = "KeepMenuItemContainer",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = dimensions.horizontalPadding,
                vertical = dimensions.verticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(dimensions.contentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                leadingContent()
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = if (enabled) {
                        colors.foreground.subtle
                    } else {
                        colors.foreground.disabled
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
