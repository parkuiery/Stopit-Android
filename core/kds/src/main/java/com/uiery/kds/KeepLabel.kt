package com.uiery.kds

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

enum class KeepLabelTone {
    Neutral,
    Muted,
    Brand,
    Critical,
}

enum class KeepLabelSize {
    Small,
    Medium,
    Large,
}

enum class KeepLabelWeight {
    Regular,
    Strong,
}

@Composable
fun KeepLabel(
    text: String,
    modifier: Modifier = Modifier,
    tone: KeepLabelTone = KeepLabelTone.Neutral,
    size: KeepLabelSize = KeepLabelSize.Medium,
    weight: KeepLabelWeight = KeepLabelWeight.Regular,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        modifier = modifier,
        text = text,
        color = keepLabelColor(tone),
        style = when (size) {
            KeepLabelSize.Small -> MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            KeepLabelSize.Medium -> MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            KeepLabelSize.Large -> MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }.copy(
            fontWeight = when (weight) {
                KeepLabelWeight.Regular -> FontWeight.Normal
                KeepLabelWeight.Strong -> FontWeight.SemiBold
            },
        ),
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
private fun keepLabelColor(tone: KeepLabelTone): Color = when (tone) {
    KeepLabelTone.Neutral -> KeepTheme.semanticColors.foreground.neutral
    KeepLabelTone.Muted -> KeepTheme.semanticColors.foreground.muted
    KeepLabelTone.Brand -> KeepTheme.semanticColors.foreground.brand
    KeepLabelTone.Critical -> KeepTheme.semanticColors.foreground.critical
}
