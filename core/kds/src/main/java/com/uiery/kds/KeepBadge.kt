package com.uiery.kds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

enum class KeepBadgeTone {
    Neutral,
    Brand,
    Critical,
}

enum class KeepBadgeVariant {
    Weak,
    Solid,
    Outline,
}

enum class KeepBadgeSize {
    Medium,
    Large,
}

@Composable
fun KeepBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: KeepBadgeTone = KeepBadgeTone.Neutral,
    variant: KeepBadgeVariant = KeepBadgeVariant.Weak,
    size: KeepBadgeSize = KeepBadgeSize.Medium,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    val colors = keepBadgeColors(tone, variant)
    val shape = RoundedCornerShape(size.radius)
    Row(
        modifier = modifier
            .background(colors.container, shape)
            .then(
                if (variant == KeepBadgeVariant.Outline) {
                    Modifier.border(1.dp, colors.stroke, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides colors.content) {
                leadingContent()
            }
        }
        Text(
            text = text,
            color = colors.content,
            style = when (size) {
                KeepBadgeSize.Medium -> MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                KeepBadgeSize.Large -> MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class KeepBadgeColors(
    val container: Color,
    val content: Color,
    val stroke: Color,
)

@Composable
private fun keepBadgeColors(
    tone: KeepBadgeTone,
    variant: KeepBadgeVariant,
): KeepBadgeColors {
    val colors = KeepTheme.semanticColors
    return when (variant) {
        KeepBadgeVariant.Weak -> when (tone) {
            KeepBadgeTone.Neutral -> KeepBadgeColors(
                colors.background.neutralWeak,
                colors.foreground.neutral,
                Color.Transparent,
            )
            KeepBadgeTone.Brand -> KeepBadgeColors(
                colors.background.brandWeak,
                colors.foreground.brand,
                Color.Transparent,
            )
            KeepBadgeTone.Critical -> KeepBadgeColors(
                colors.background.criticalWeak,
                colors.foreground.critical,
                Color.Transparent,
            )
        }
        KeepBadgeVariant.Solid -> when (tone) {
            KeepBadgeTone.Neutral -> KeepBadgeColors(
                colors.background.neutralInverted,
                colors.foreground.inverted,
                Color.Transparent,
            )
            KeepBadgeTone.Brand -> KeepBadgeColors(
                colors.background.brandSolid,
                colors.foreground.onBrand,
                Color.Transparent,
            )
            KeepBadgeTone.Critical -> KeepBadgeColors(
                colors.background.criticalSolid,
                colors.foreground.onCritical,
                Color.Transparent,
            )
        }
        KeepBadgeVariant.Outline -> when (tone) {
            KeepBadgeTone.Neutral -> KeepBadgeColors(
                colors.background.transparent,
                colors.foreground.neutral,
                colors.stroke.neutralSolid,
            )
            KeepBadgeTone.Brand -> KeepBadgeColors(
                colors.background.transparent,
                colors.foreground.brand,
                colors.stroke.brandSolid,
            )
            KeepBadgeTone.Critical -> KeepBadgeColors(
                colors.background.transparent,
                colors.foreground.critical,
                colors.stroke.criticalSolid,
            )
        }
    }
}

private val KeepBadgeSize.radius: Dp
    get() = when (this) {
        KeepBadgeSize.Medium -> 6.dp
        KeepBadgeSize.Large -> 8.dp
    }

private val KeepBadgeSize.horizontalPadding: Dp
    get() = when (this) {
        KeepBadgeSize.Medium -> 6.dp
        KeepBadgeSize.Large -> 8.dp
    }

private val KeepBadgeSize.verticalPadding: Dp
    get() = when (this) {
        KeepBadgeSize.Medium -> 2.dp
        KeepBadgeSize.Large -> 4.dp
    }
