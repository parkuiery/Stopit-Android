package com.uiery.keep.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import com.uiery.kds.KeepChip
import com.uiery.kds.KeepChipRole
import com.uiery.kds.KeepChipVariant
import com.uiery.kds.KeepDivider
import com.uiery.kds.KeepLabel
import com.uiery.kds.KeepLabelSize
import com.uiery.kds.KeepLabelTone
import com.uiery.kds.KeepLabelWeight
import com.uiery.kds.KeepSelectableCard
import com.uiery.kds.KeepTextField
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.util.rememberAppDisplayMetadataResolver

/**
 * Shared KDS-styled building blocks for "setup" style registration screens
 * (Goal Lock creation, Parent Mode setup). They mirror the design language used by
 * EmergencyUnlockSettings so registration flows feel consistent and on-brand.
 */

@Composable
fun SetupHero(
    iconResId: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val accent = KeepTheme.colors.primary
    KeepCard(
        modifier = modifier
            .fillMaxWidth(),
        variant = KeepCardVariant.BrandWeak,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = accent,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = KeepTheme.semanticColors.foreground.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
        }
    }
}

@Composable
fun SetupGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    KeepCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}

@Composable
fun SetupSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    valueLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeepLabel(
            modifier = Modifier.weight(1f),
            text = title,
            size = KeepLabelSize.Large,
            weight = KeepLabelWeight.Strong,
        )
        if (valueLabel != null) {
            KeepLabel(
                text = valueLabel,
                tone = KeepLabelTone.Brand,
                size = KeepLabelSize.Medium,
                weight = KeepLabelWeight.Strong,
            )
        }
    }
}

@Composable
fun SetupSectionCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    KeepLabel(
        modifier = modifier.fillMaxWidth(),
        text = text,
        tone = KeepLabelTone.Muted,
        size = KeepLabelSize.Small,
    )
}

@Composable
fun SetupGroupDivider() {
    KeepDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

/**
 * A selectable option card with a leading radio indicator. Used for mutually exclusive
 * choices (e.g. lock mode, refill mode).
 */
@Composable
fun SetupSelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) = KeepSelectableCard(
    modifier = modifier,
    title = title,
    description = subtitle,
    selected = selected,
    enabled = enabled,
    contentDescription = contentDescription,
    onClick = onClick,
)

/** Pill-style chip for quick presets (e.g. durations). */
@Composable
fun SetupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = KeepChip(
    modifier = modifier,
    text = label,
    selected = selected,
    enabled = enabled,
    variant = KeepChipVariant.OutlineStrong,
    role = KeepChipRole.Radio,
    onClick = onClick,
)

/** A tonal secondary action button that stays subordinate to the primary KeepButton. */
@Composable
fun SetupSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIconResId: Int? = null,
) {
    val containerColor = KeepTheme.colors.tertiary.copy(alpha = if (enabled) 0.55f else 0.30f)
    val contentColor = KeepTheme.colors.surfaceVariant.copy(alpha = if (enabled) 1f else 0.45f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconResId != null) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(id = leadingIconResId),
                contentDescription = null,
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A stepper for incrementing/decrementing an integer value with +/- controls. */
@Composable
fun SetupStepper(
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.background)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(symbol = "−", enabled = decrementEnabled, onClick = onDecrement)
        Text(
            modifier = Modifier.weight(1f),
            text = valueLabel,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        StepperButton(symbol = "+", enabled = incrementEnabled, onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KeepTheme.colors.onSecondary)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = if (enabled) KeepTheme.colors.primary else KeepTheme.colors.onTertiaryContainer,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A row showing an app's icon + label, with an optional trailing remove control. */
@Composable
fun SetupAppRow(
    packageName: String,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    val resolver = rememberAppDisplayMetadataResolver()
    val metadata = remember(packageName, resolver) { resolver.resolve(packageName) }
    val label = metadata.label.takeIf { it.isNotBlank() && it != packageName } ?: fallbackLabel

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            bitmap = metadata.icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRemove != null && removeLabel != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRemove)
                    .semantics { this.contentDescription = removeLabel }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = removeLabel,
                    color = KeepTheme.colors.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A KDS-styled filled text field used across setup screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
) {
    KeepTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(text = placeholder)
        },
        singleLine = true,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

fun Modifier.dimWhen(condition: Boolean): Modifier =
    if (condition) this.alpha(0.45f) else this
