package com.uiery.kds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.uiery.kds.theme.KeepTheme

enum class KeepConfirmationTone {
    Brand,
    Neutral,
    Critical,
}

@Composable
fun KeepAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(dismissOnClickOutside = false),
) {
    KeepDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
    ) {
        Column(
            modifier = Modifier.padding(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.invoke()
            title?.invoke()
            text?.invoke()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dismissButton?.invoke()
            confirmButton()
        }
    }
}

@Composable
fun KeepConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTone: KeepConfirmationTone = KeepConfirmationTone.Neutral,
    properties: DialogProperties = DialogProperties(dismissOnClickOutside = false),
) {
    KeepAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = message,
                color = KeepTheme.semanticColors.foreground.neutral,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            KeepDialogActionPair(
                dismissLabel = dismissLabel,
                confirmLabel = confirmLabel,
                confirmTone = confirmTone,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
            )
        },
        properties = properties,
    )
}

@Composable
private fun KeepDialogActionPair(
    dismissLabel: String,
    confirmLabel: String,
    confirmTone: KeepConfirmationTone,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val confirmVariant = when (confirmTone) {
        KeepConfirmationTone.Brand -> KeepButtonVariant.BrandSolid
        KeepConfirmationTone.Neutral -> KeepButtonVariant.NeutralSolid
        KeepConfirmationTone.Critical -> KeepButtonVariant.CriticalSolid
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val dismissButtonWidth = with(density) {
            textMeasurer.measure(dismissLabel, labelStyle).size.width.toDp() + 32.dp
        }
        val confirmButtonWidth = with(density) {
            textMeasurer.measure(confirmLabel, labelStyle).size.width.toDp() + 32.dp
        }
        val availableButtonWidth = (maxWidth - 8.dp) / 2
        val stackVertically =
            dismissButtonWidth > availableButtonWidth ||
                confirmButtonWidth > availableButtonWidth
        if (stackVertically) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = dismissLabel,
                    variant = KeepButtonVariant.NeutralWeak,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onDismiss,
                )
                KeepButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = confirmLabel,
                    variant = confirmVariant,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onConfirm,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeepButton(
                    modifier = Modifier.weight(1f),
                    text = dismissLabel,
                    variant = KeepButtonVariant.NeutralWeak,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onDismiss,
                )
                KeepButton(
                    modifier = Modifier.weight(1f),
                    text = confirmLabel,
                    variant = confirmVariant,
                    size = KeepButtonSize.Medium,
                    bottomSpacing = false,
                    onClick = onConfirm,
                )
            }
        }
    }
}
