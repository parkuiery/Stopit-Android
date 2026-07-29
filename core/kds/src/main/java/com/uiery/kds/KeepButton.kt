package com.uiery.kds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

/** SEED Action Button variants. */
enum class KeepButtonVariant {
    BrandSolid,
    NeutralSolid,
    NeutralWeak,
    BrandOutline,
    NeutralOutline,
    CriticalSolid,
    Ghost,
}

/** SEED Action Button sizes. */
enum class KeepButtonSize {
    XSmall,
    Small,
    Medium,
    Large,
}

@Composable
fun KeepButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: KeepButtonVariant = KeepButtonVariant.BrandSolid,
    size: KeepButtonSize = KeepButtonSize.Large,
    bottomSpacing: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val specification = buttonSpecification(
        variant = variant,
        enabled = enabled,
    )
    val containerColor by animateColorAsState(
        targetValue = if (isPressed || loading) {
            specification.pressedContainerColor
        } else {
            specification.containerColor
        },
        label = "KeepButtonContainerColor",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) size.pressedScale else 1f,
        label = "KeepButtonPressedScale",
    )
    val outerModifier = (if (bottomSpacing) {
        modifier.padding(bottom = 24.dp)
    } else {
        modifier
    })
        .height(size.height)
        .graphicsLayer {
            scaleX = pressedScale
            scaleY = pressedScale
        }
        .then(
            if (loading) {
                Modifier.semantics {
                    contentDescription = text
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            } else {
                Modifier
            },
        )

    Button(
        modifier = outerModifier,
        onClick = onClick,
        shape = size.shape,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = specification.contentColor,
            disabledContainerColor = KeepTheme.semanticColors.background.disabled,
            disabledContentColor = KeepTheme.semanticColors.foreground.disabled,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        border = specification.border,
        contentPadding = size.contentPadding,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = KeepTheme.semanticColors.foreground.disabled,
                trackColor = KeepTheme.semanticColors.background.disabled,
                strokeWidth = 2.dp,
                modifier = Modifier.size(size.progressSize),
            )
        } else {
            Text(
                text = text,
                style = size.textStyle,
            )
        }
    }
}

private data class ButtonSpecification(
    val containerColor: Color,
    val pressedContainerColor: Color,
    val contentColor: Color,
    val loadingTrackColor: Color,
    val border: BorderStroke?,
)

@Composable
private fun buttonSpecification(
    variant: KeepButtonVariant,
    enabled: Boolean,
): ButtonSpecification {
    val colors = KeepTheme.semanticColors
    val disabledBorder = colors.stroke.neutralMuted
    return when (variant) {
        KeepButtonVariant.BrandSolid -> ButtonSpecification(
            containerColor = colors.background.brandSolid,
            pressedContainerColor = colors.background.brandSolidPressed,
            contentColor = colors.foreground.onBrand,
            loadingTrackColor = colors.foreground.onBrand.copy(alpha = 0.30f),
            border = null,
        )
        KeepButtonVariant.NeutralSolid -> ButtonSpecification(
            containerColor = colors.background.neutralInverted,
            pressedContainerColor = colors.background.neutralInvertedPressed,
            contentColor = colors.foreground.inverted,
            loadingTrackColor = colors.static.whiteAlpha300,
            border = null,
        )
        KeepButtonVariant.NeutralWeak -> ButtonSpecification(
            containerColor = colors.background.neutralWeak,
            pressedContainerColor = colors.background.neutralWeakPressed,
            contentColor = colors.foreground.neutral,
            loadingTrackColor = colors.foreground.disabled,
            border = null,
        )
        KeepButtonVariant.BrandOutline -> ButtonSpecification(
            containerColor = colors.background.transparent,
            pressedContainerColor = colors.background.transparentPressed,
            contentColor = colors.foreground.brand,
            loadingTrackColor = colors.background.brandWeakPressed,
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) colors.stroke.brandSolid else disabledBorder,
            ),
        )
        KeepButtonVariant.NeutralOutline -> ButtonSpecification(
            containerColor = colors.background.transparent,
            pressedContainerColor = colors.background.transparentPressed,
            contentColor = colors.foreground.neutral,
            loadingTrackColor = colors.foreground.disabled,
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) colors.stroke.neutralSolid else disabledBorder,
            ),
        )
        KeepButtonVariant.CriticalSolid -> ButtonSpecification(
            containerColor = colors.background.criticalSolid,
            pressedContainerColor = colors.background.criticalSolidPressed,
            contentColor = colors.static.white,
            loadingTrackColor = colors.static.whiteAlpha300,
            border = null,
        )
        KeepButtonVariant.Ghost -> ButtonSpecification(
            containerColor = colors.background.transparent,
            pressedContainerColor = colors.background.transparentPressed,
            contentColor = colors.foreground.neutral,
            loadingTrackColor = colors.foreground.disabled,
            border = null,
        )
    }
}

private val KeepButtonSize.height: Dp
    get() = when (this) {
        KeepButtonSize.XSmall -> 32.dp
        KeepButtonSize.Small -> 36.dp
        KeepButtonSize.Medium -> 40.dp
        KeepButtonSize.Large -> 52.dp
    }

private val KeepButtonSize.shape: RoundedCornerShape
    get() = when (this) {
        KeepButtonSize.XSmall -> RoundedCornerShape(9999.dp)
        KeepButtonSize.Small,
        KeepButtonSize.Medium,
        -> RoundedCornerShape(8.dp)
        KeepButtonSize.Large -> RoundedCornerShape(12.dp)
    }

private val KeepButtonSize.contentPadding: PaddingValues
    get() = when (this) {
        KeepButtonSize.XSmall -> PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        KeepButtonSize.Small -> PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        KeepButtonSize.Medium -> PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        KeepButtonSize.Large -> PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    }

private val KeepButtonSize.textStyle
    @Composable get() = when (this) {
        KeepButtonSize.XSmall -> MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        KeepButtonSize.Small,
        KeepButtonSize.Medium,
        -> MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        KeepButtonSize.Large -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        )
    }

private val KeepButtonSize.pressedScale: Float
    get() = when (this) {
        KeepButtonSize.XSmall -> 0.95f
        KeepButtonSize.Small,
        KeepButtonSize.Medium,
        -> 0.97f
        KeepButtonSize.Large -> 0.98f
    }

private val KeepButtonSize.progressSize: Dp
    get() = when (this) {
        KeepButtonSize.XSmall,
        KeepButtonSize.Small,
        -> 14.dp
        KeepButtonSize.Medium -> 16.dp
        KeepButtonSize.Large -> 22.dp
    }
