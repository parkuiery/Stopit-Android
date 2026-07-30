package com.uiery.kds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

enum class KeepTextFieldVariant {
    Outline,
    Underline,
}

enum class KeepFieldRequirement {
    None,
    Required,
    Optional,
}

enum class KeepFieldLabelWeight {
    Medium,
    Bold,
}

enum class KeepFieldHelperTone {
    Subtle,
    Muted,
}

@Immutable
internal data class KeepTextInputDimensions(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val cornerRadius: Dp,
)

internal fun keepTextInputDimensions(
    variant: KeepTextFieldVariant,
): KeepTextInputDimensions = when (variant) {
    KeepTextFieldVariant.Outline -> KeepTextInputDimensions(
        minHeight = 52.dp,
        horizontalPadding = 16.dp,
        cornerRadius = 12.dp,
    )
    KeepTextFieldVariant.Underline -> KeepTextInputDimensions(
        minHeight = 40.dp,
        horizontalPadding = 0.dp,
        cornerRadius = 0.dp,
    )
}

internal fun keepTextInputMinHeight(
    variant: KeepTextFieldVariant,
    singleLine: Boolean,
): Dp = if (singleLine) {
    keepTextInputDimensions(variant).minHeight
} else {
    95.dp
}

@Immutable
internal data class KeepFieldMessage(
    val text: String,
    val isError: Boolean,
)

internal fun resolveKeepFieldMessage(
    helperText: String?,
    errorMessage: String?,
): KeepFieldMessage? = when {
    !errorMessage.isNullOrBlank() -> KeepFieldMessage(errorMessage, isError = true)
    !helperText.isNullOrBlank() -> KeepFieldMessage(helperText, isError = false)
    else -> null
}

/**
 * SEED Field container. Header, input and footer are kept separate so non-text controls can
 * share the same label, validation and character-count behavior.
 */
@Composable
fun KeepField(
    modifier: Modifier = Modifier,
    label: String? = null,
    requirement: KeepFieldRequirement = KeepFieldRequirement.None,
    optionalText: String? = null,
    labelWeight: KeepFieldLabelWeight = KeepFieldLabelWeight.Medium,
    helperText: String? = null,
    helperTextTone: KeepFieldHelperTone = KeepFieldHelperTone.Subtle,
    errorMessage: String? = null,
    characterCount: Int? = null,
    maxCharacterCount: Int? = null,
    headerSuffix: (@Composable () -> Unit)? = null,
    input: @Composable (isError: Boolean) -> Unit,
) {
    val message = resolveKeepFieldMessage(helperText, errorMessage)
    val isError = message?.isError == true
    val hasHeader = label != null || headerSuffix != null
    val hasFooter = message != null || characterCount != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = KeepTheme.semanticColors.foreground.neutral,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = when (labelWeight) {
                                KeepFieldLabelWeight.Medium -> FontWeight.Medium
                                KeepFieldLabelWeight.Bold -> FontWeight.Bold
                            },
                        ),
                        maxLines = 2,
                    )
                    when (requirement) {
                        KeepFieldRequirement.Required -> Text(
                            modifier = Modifier.padding(start = 2.dp, top = 1.dp),
                            text = "*",
                            color = KeepTheme.semanticColors.foreground.critical,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        KeepFieldRequirement.Optional -> if (optionalText != null) {
                            Text(
                                modifier = Modifier.padding(start = 4.dp),
                                text = optionalText,
                                color = KeepTheme.semanticColors.foreground.subtle,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        KeepFieldRequirement.None -> Unit
                    }
                }
                if (headerSuffix != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    headerSuffix()
                }
            }
        }

        input(isError)

        if (hasFooter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (message != null) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = message.text,
                        color = if (message.isError) {
                            KeepTheme.semanticColors.foreground.critical
                        } else {
                            when (helperTextTone) {
                                KeepFieldHelperTone.Subtle ->
                                    KeepTheme.semanticColors.foreground.subtle
                                KeepFieldHelperTone.Muted ->
                                    KeepTheme.semanticColors.foreground.muted
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (characterCount != null) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = buildString {
                            append(characterCount)
                            if (maxCharacterCount != null) append(" / $maxCharacterCount")
                        },
                        color = if (isError) {
                            KeepTheme.semanticColors.foreground.critical
                        } else {
                            KeepTheme.semanticColors.foreground.neutral
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * SEED Text Input shell. This intentionally uses [BasicTextField] so Material floating-label,
 * filled-container and brand-focus defaults cannot leak into KDS.
 */
@Composable
fun KeepTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    accessibilityLabel: String? = null,
    showClearButton: Boolean = false,
    clearButtonContentDescription: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    variant: KeepTextFieldVariant = KeepTextFieldVariant.Outline,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val dimensions = keepTextInputDimensions(variant)
    val semanticColors = KeepTheme.semanticColors
    val strokeColor = when {
        !enabled -> semanticColors.stroke.neutralMuted
        isError -> semanticColors.stroke.criticalSolid
        isFocused -> semanticColors.stroke.neutralContrast
        else -> semanticColors.stroke.neutralWeak
    }
    val strokeWidth = if (isFocused || isError) 2.dp else 1.dp
    val valueColor = when {
        !enabled -> semanticColors.foreground.disabled
        readOnly && variant == KeepTextFieldVariant.Underline -> semanticColors.foreground.muted
        else -> semanticColors.foreground.neutral
    }
    val slotColor = if (enabled) {
        semanticColors.foreground.muted
    } else {
        semanticColors.foreground.disabled
    }
    val containerColor = when {
        variant == KeepTextFieldVariant.Underline -> Color.Transparent
        !enabled || readOnly -> semanticColors.background.disabled
        else -> semanticColors.background.layerDefault
    }
    val shape = RoundedCornerShape(dimensions.cornerRadius)
    val inputTextStyle = when (variant) {
        KeepTextFieldVariant.Outline -> MaterialTheme.typography.bodyLarge
        KeepTextFieldVariant.Underline -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
        )
    }.copy(color = valueColor)
    val containerModifier = when (variant) {
        KeepTextFieldVariant.Outline -> Modifier.border(strokeWidth, strokeColor, shape)
        KeepTextFieldVariant.Underline -> Modifier.drawBehind {
            val widthPx = strokeWidth.toPx()
            drawLine(
                color = strokeColor,
                start = Offset(0f, size.height - widthPx / 2f),
                end = Offset(size.width, size.height - widthPx / 2f),
                strokeWidth = widthPx,
            )
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .semantics {
                if (!accessibilityLabel.isNullOrBlank()) {
                    contentDescription = accessibilityLabel
                }
                if (!errorMessage.isNullOrBlank()) error(errorMessage)
            }
            .then(containerModifier)
            .defaultMinSize(
                minHeight = keepTextInputMinHeight(
                    variant = variant,
                    singleLine = singleLine,
                ),
            )
            .background(containerColor, shape)
            .padding(
                horizontal = dimensions.horizontalPadding,
                vertical = if (singleLine) 0.dp else 14.dp,
            ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = inputTextStyle,
        cursorBrush = SolidColor(
            if (isError) semanticColors.foreground.critical
            else semanticColors.foreground.neutral,
        ),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingContent != null) {
                    CompositionLocalProvider(LocalContentColor provides slotColor) {
                        leadingContent()
                    }
                }
                if (prefix != null) {
                    CompositionLocalProvider(LocalContentColor provides slotColor) {
                        prefix()
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides if (enabled) {
                                semanticColors.foreground.placeholder
                            } else {
                                semanticColors.foreground.disabled
                            },
                        ) {
                            placeholder()
                        }
                    }
                    innerTextField()
                }
                if (suffix != null) {
                    CompositionLocalProvider(LocalContentColor provides slotColor) {
                        suffix()
                    }
                }
                val canClear = showClearButton &&
                    value.isNotEmpty() &&
                    enabled &&
                    !readOnly &&
                    clearButtonContentDescription != null
                if (canClear) {
                    KeepTextInputClearButton(
                        contentDescription = clearButtonContentDescription,
                        onClick = { onValueChange("") },
                    )
                } else if (trailingContent != null) {
                    CompositionLocalProvider(LocalContentColor provides slotColor) {
                        trailingContent()
                    }
                }
            }
        },
    )
}

/**
 * Convenience template that composes SEED Field and Text Input.
 */
@Composable
fun KeepTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    fieldLabel: String? = null,
    requirement: KeepFieldRequirement = KeepFieldRequirement.None,
    optionalText: String? = null,
    labelWeight: KeepFieldLabelWeight = KeepFieldLabelWeight.Medium,
    helperText: String? = null,
    helperTextTone: KeepFieldHelperTone = KeepFieldHelperTone.Subtle,
    errorMessage: String? = null,
    maxCharacterCount: Int? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    showClearButton: Boolean = false,
    clearButtonContentDescription: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    variant: KeepTextFieldVariant = KeepTextFieldVariant.Outline,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val resolvedError = errorMessage?.takeIf { it.isNotBlank() }
    KeepField(
        modifier = modifier,
        label = fieldLabel,
        requirement = requirement,
        optionalText = optionalText,
        labelWeight = labelWeight,
        helperText = helperText,
        helperTextTone = helperTextTone,
        errorMessage = resolvedError,
        characterCount = maxCharacterCount?.let { value.length },
        maxCharacterCount = maxCharacterCount,
    ) { fieldHasError ->
        KeepTextInput(
            value = value,
            onValueChange = { changedValue ->
                onValueChange(
                    maxCharacterCount?.let { changedValue.take(it) } ?: changedValue,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            placeholder = placeholder,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            prefix = prefix,
            suffix = suffix,
            isError = isError || fieldHasError,
            errorMessage = resolvedError,
            accessibilityLabel = fieldLabel,
            showClearButton = showClearButton,
            clearButtonContentDescription = clearButtonContentDescription,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            variant = variant,
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun KeepTextInputClearButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val backgroundColor = KeepTheme.semanticColors.foreground.muted
    val glyphColor = KeepTheme.semanticColors.background.layerDefault

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(backgroundColor)
            val inset = size.minDimension * 0.32f
            drawLine(
                color = glyphColor,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = glyphColor,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
