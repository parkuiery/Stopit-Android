package com.uiery.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

enum class KeepChipVariant {
    Solid,
    OutlineStrong,
    OutlineWeak,
}

enum class KeepChipSize {
    Small,
    Medium,
    Large,
}

enum class KeepChipRole {
    Action,
    Toggle,
    Radio,
}

@Composable
fun KeepChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    variant: KeepChipVariant = KeepChipVariant.OutlineStrong,
    size: KeepChipSize = KeepChipSize.Medium,
    role: KeepChipRole = KeepChipRole.Toggle,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val indication = LocalIndication.current
    val colors = keepChipColors(variant, selected, enabled, isPressed)
    val containerColor by animateColorAsState(colors.container, label = "KeepChipContainer")
    val contentColor by animateColorAsState(colors.content, label = "KeepChipContent")
    val borderColor by animateColorAsState(colors.border, label = "KeepChipBorder")
    val interactionModifier = when (role) {
        KeepChipRole.Action -> Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
        )
        KeepChipRole.Toggle -> Modifier.toggleable(
            value = selected,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = interactionSource,
            indication = indication,
            onValueChange = { onClick() },
        )
        KeepChipRole.Radio -> Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
        )
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .then(
                if (colors.borderWidth > 0.dp) {
                    Modifier.border(
                        width = colors.borderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(999.dp),
                    )
                } else {
                    Modifier
                },
            )
            .then(interactionModifier)
            .padding(size.contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = size.textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class KeepChipColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val borderWidth: Dp,
)

@Composable
private fun keepChipColors(
    variant: KeepChipVariant,
    selected: Boolean,
    enabled: Boolean,
    pressed: Boolean,
): KeepChipColors {
    val colors = KeepTheme.semanticColors
    if (!enabled) {
        return KeepChipColors(
            container = colors.background.disabled,
            content = colors.foreground.disabled,
            border = colors.stroke.neutralMuted,
            borderWidth = if (variant == KeepChipVariant.Solid) 0.dp else 1.dp,
        )
    }
    return when (variant) {
        KeepChipVariant.Solid -> KeepChipColors(
            container = when {
                selected && pressed -> colors.background.brandSolidPressed
                selected -> colors.background.brandSolid
                pressed -> colors.background.neutralWeakPressed
                else -> colors.background.neutralWeak
            },
            content = if (selected) colors.foreground.onBrand else colors.foreground.neutral,
            border = Color.Transparent,
            borderWidth = 0.dp,
        )
        KeepChipVariant.OutlineStrong -> KeepChipColors(
            container = when {
                selected && pressed -> colors.background.brandWeakPressed
                selected -> colors.background.brandWeak
                pressed -> colors.background.transparentPressed
                else -> colors.background.transparent
            },
            content = if (selected) colors.foreground.brand else colors.foreground.neutral,
            border = if (selected) colors.stroke.brandSolid else colors.stroke.neutralSolid,
            borderWidth = 1.dp,
        )
        KeepChipVariant.OutlineWeak -> KeepChipColors(
            container = when {
                selected && pressed -> colors.background.brandWeakPressed
                selected -> colors.background.brandWeak
                pressed -> colors.background.transparentPressed
                else -> colors.background.transparent
            },
            content = if (selected) colors.foreground.brand else colors.foreground.neutral,
            border = if (selected) colors.stroke.brandWeak else colors.stroke.neutralWeak,
            borderWidth = 1.dp,
        )
    }
}

private val KeepChipSize.height: Dp
    get() = when (this) {
        KeepChipSize.Small -> 32.dp
        KeepChipSize.Medium -> 40.dp
        KeepChipSize.Large -> 48.dp
    }

private val KeepChipSize.contentPadding: PaddingValues
    get() = when (this) {
        KeepChipSize.Small -> PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        KeepChipSize.Medium -> PaddingValues(horizontal = 16.dp, vertical = 9.dp)
        KeepChipSize.Large -> PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    }

private val KeepChipSize.textStyle
    @Composable get() = when (this) {
        KeepChipSize.Small -> MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        KeepChipSize.Medium -> MaterialTheme.typography.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        )
        KeepChipSize.Large -> MaterialTheme.typography.titleSmall.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
