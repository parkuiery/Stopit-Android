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

/**
 * @param readOnly 지금은 누를 수 없지만 내용은 계속 읽혀야 할 때 사용한다.
 *
 * SEED의 `bg.disabled`는 컨트롤이 `layer-default` 위에 놓인다는 전제에서 정해졌고, light 모드에서
 * `layer-basement`와 같은 `gray-200`이다. 화면 캔버스 위의 카드에 그대로 쓰면 면과 글자가 함께
 * 배경에 묻혀 담고 있던 정보까지 사라진다. read-only는 variant container를 유지해 카드를 남기고,
 * 조작 불가는 `foreground.muted`와 주변 아이콘으로 전달한다.
 */
@Composable
fun KeepCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    variant: KeepCardVariant = KeepCardVariant.LayerDefault,
    bordered: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !readOnly,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = keepCardContainerColor(variant),
            contentColor = keepCardContentColor(variant),
            disabledContainerColor = if (readOnly) {
                keepCardContainerColor(variant)
            } else {
                KeepTheme.semanticColors.background.disabled
            },
            disabledContentColor = if (readOnly) {
                KeepTheme.semanticColors.foreground.muted
            } else {
                KeepTheme.semanticColors.foreground.disabled
            },
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
