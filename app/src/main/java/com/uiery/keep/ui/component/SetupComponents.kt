package com.uiery.keep.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
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
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
fun SetupGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KeepTheme.colors.onSecondary)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .animateContentSize(),
        content = content,
    )
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
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            color = KeepTheme.colors.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (valueLabel != null) {
            Text(
                text = valueLabel,
                color = KeepTheme.colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun SetupSectionCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = text,
        color = KeepTheme.colors.onTertiaryContainer,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
}

@Composable
fun SetupGroupDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(1.dp)
            .background(KeepTheme.colors.tertiary.copy(alpha = 0.4f)),
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
) {
    val borderColor = if (selected) KeepTheme.colors.primary else KeepTheme.colors.tertiary.copy(alpha = 0.5f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KeepTheme.colors.background)
            .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(width = 2.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(KeepTheme.colors.primary),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = KeepTheme.colors.onTertiaryContainer,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** Pill-style chip for quick presets (e.g. durations). */
@Composable
fun SetupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val primary = KeepTheme.colors.primary
    val containerColor = if (selected) primary.copy(alpha = 0.12f) else KeepTheme.colors.background
    val borderColor = if (selected) primary else KeepTheme.colors.tertiary.copy(alpha = 0.6f)
    val textColor = if (selected) primary else KeepTheme.colors.onSurfaceVariant
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** A tonal secondary action button that stays subordinate to the primary KeepButton. */
@Composable
fun SetupSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIconResId: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KeepTheme.colors.primary.copy(alpha = if (enabled) 0.10f else 0.05f))
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
                tint = KeepTheme.colors.primary.copy(alpha = if (enabled) 1f else 0.4f),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = KeepTheme.colors.primary.copy(alpha = if (enabled) 1f else 0.4f),
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
                    color = KeepTheme.colors.primary,
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
    TextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = KeepTheme.colors.onTertiaryContainer,
            )
        },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = KeepTheme.colors.background,
            unfocusedContainerColor = KeepTheme.colors.background,
            disabledContainerColor = KeepTheme.colors.background,
            errorContainerColor = KeepTheme.colors.background,
            focusedIndicatorColor = KeepTheme.colors.primary,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = KeepTheme.colors.error,
            cursorColor = KeepTheme.colors.primary,
            focusedTextColor = KeepTheme.colors.onSurfaceVariant,
            unfocusedTextColor = KeepTheme.colors.onSurfaceVariant,
        ),
    )
}

fun Modifier.dimWhen(condition: Boolean): Modifier =
    if (condition) this.alpha(0.45f) else this
