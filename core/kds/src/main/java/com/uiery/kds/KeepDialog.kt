package com.uiery.kds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uiery.kds.theme.KeepTheme

/**
 * Floating dialog container following SEED's dialog dimensions and layer roles.
 *
 * Use [KeepAlertDialog] for confirmations and warnings. This lower-level container is
 * reserved for structured interactive content such as wheel pickers.
 */
@Composable
fun KeepDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            modifier = modifier
                .widthIn(max = 272.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = KeepTheme.semanticColors.background.layerFloating,
            contentColor = KeepTheme.semanticColors.foreground.neutral,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(content = content)
        }
    }
}
