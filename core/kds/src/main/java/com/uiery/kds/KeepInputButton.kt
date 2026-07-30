package com.uiery.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@Immutable
internal data class KeepInputButtonDimensions(
    val height: Dp,
    val horizontalPadding: Dp,
    val cornerRadius: Dp,
    val strokeWidth: Dp,
)

internal fun keepInputButtonDimensions(): KeepInputButtonDimensions =
    KeepInputButtonDimensions(
        height = 52.dp,
        horizontalPadding = 16.dp,
        cornerRadius = 12.dp,
        strokeWidth = 1.dp,
    )

/**
 * SEED Input Button. Use this control to open a picker or selection surface; it is not an
 * editable text field.
 */
@Composable
fun KeepInputButton(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    contentDescription: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val dimensions = keepInputButtonDimensions()
    val colors = KeepTheme.semanticColors
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteractive = enabled && !readOnly
    val hasValue = !value.isNullOrBlank()
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled || readOnly -> colors.background.disabled
            isPressed -> colors.background.transparentPressed
            else -> colors.background.transparent
        },
        label = "KeepInputButtonContainer",
    )
    val contentColor = when {
        !enabled -> colors.foreground.disabled
        hasValue -> colors.foreground.neutral
        else -> colors.foreground.placeholder
    }
    val slotColor = if (enabled) {
        colors.foreground.muted
    } else {
        colors.foreground.disabled
    }
    val strokeColor = when {
        !enabled -> colors.stroke.neutralMuted
        isError -> colors.stroke.criticalSolid
        else -> colors.stroke.neutralWeak
    }
    val strokeWidth = if (isError) 2.dp else dimensions.strokeWidth
    val shape = RoundedCornerShape(dimensions.cornerRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.height)
            .background(containerColor, shape)
            .border(strokeWidth, strokeColor, shape)
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier.semantics { role = Role.Button }
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = dimensions.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides slotColor) {
                leadingContent()
            }
        }
        Text(
            modifier = Modifier.weight(1f),
            text = value?.takeIf { it.isNotBlank() } ?: placeholder,
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(2.dp))
            CompositionLocalProvider(LocalContentColor provides slotColor) {
                trailingContent()
            }
        }
    }
}
