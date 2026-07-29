package com.uiery.kds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

enum class KeepCardVariant {
    LayerDefault,
    NeutralWeak,
    NeutralMuted,
    BrandWeak,
    BrandSolid,
    CriticalWeak,
}

@Composable
fun KeepCard(
    modifier: Modifier = Modifier,
    variant: KeepCardVariant = KeepCardVariant.LayerDefault,
    bordered: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = keepCardContainerColor(variant),
            contentColor = keepCardContentColor(variant),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (bordered) {
            BorderStroke(1.dp, KeepTheme.semanticColors.stroke.neutralWeak)
        } else {
            null
        },
        content = content,
    )
}

@Composable
fun KeepCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: KeepCardVariant = KeepCardVariant.LayerDefault,
    bordered: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = keepCardContainerColor(variant),
            contentColor = keepCardContentColor(variant),
            disabledContainerColor = KeepTheme.semanticColors.background.disabled,
            disabledContentColor = KeepTheme.semanticColors.foreground.disabled,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (bordered) {
            BorderStroke(1.dp, KeepTheme.semanticColors.stroke.neutralWeak)
        } else {
            null
        },
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun keepCardContainerColor(variant: KeepCardVariant): Color = when (variant) {
    KeepCardVariant.LayerDefault -> KeepTheme.semanticColors.background.layerDefault
    KeepCardVariant.NeutralWeak -> KeepTheme.semanticColors.background.neutralWeak
    KeepCardVariant.NeutralMuted -> KeepTheme.semanticColors.background.neutralMuted
    KeepCardVariant.BrandWeak -> KeepTheme.semanticColors.background.brandWeak
    KeepCardVariant.BrandSolid -> KeepTheme.semanticColors.background.brandSolid
    KeepCardVariant.CriticalWeak -> KeepTheme.semanticColors.background.criticalWeak
}

@Composable
private fun keepCardContentColor(variant: KeepCardVariant): Color = when (variant) {
    KeepCardVariant.BrandSolid -> KeepTheme.semanticColors.foreground.onBrand
    else -> KeepTheme.semanticColors.foreground.neutral
}
