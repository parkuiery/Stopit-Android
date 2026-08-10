package com.uiery.keep.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.appselection.SensitiveAppRole
import com.uiery.keep.appselection.SensitiveAppSelection

/**
 * Confirms the device-role apps a selection is about to block.
 *
 * These apps were hidden from the picker in 1.7.12 because "select all" swept them in with no
 * warning. Hiding them also made it impossible to block a messaging app on purpose, which is a core
 * reason people install a focus app, so they are selectable again and this is the disclosure that
 * replaces the removal.
 *
 * Both buttons commit — there is no "cancel and go back". The user already asked to save; this only
 * decides whether the sensitive apps go with it. Naming a specific cost per app is the point:
 * "you cannot send or check texts" lands, "these are system apps" does not.
 *
 * Laid out as rows rather than one text block. Four items separated by newlines read as a paragraph,
 * and once a reason wraps, its overflow lines up with the next app's name — the reader can no longer
 * see where an item ends. Spacing does that job here, and the app name carries the weight because it
 * is what someone scans for.
 */
@Composable
fun SensitiveAppBlockingDialog(
    selections: List<SensitiveAppSelection>,
    onBlockAll: () -> Unit,
    onExcludeSensitiveApps: () -> Unit,
) {
    if (selections.isEmpty()) return

    KeepConfirmationDialog(
        title = stringResource(R.string.sensitive_block_confirm_title),
        confirmLabel = stringResource(R.string.sensitive_block_confirm_include),
        dismissLabel = stringResource(R.string.sensitive_block_confirm_exclude),
        // Blocking them is the consequential branch, and blocking Settings is the hardest of all to
        // undo, so the confirm action does not get to look routine.
        confirmTone = KeepConfirmationTone.Critical,
        onConfirm = onBlockAll,
        onDismiss = onExcludeSensitiveApps,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            selections.forEach { selection ->
                SensitiveAppRow(selection)
            }
        }
    }
}

@Composable
private fun SensitiveAppRow(selection: SensitiveAppSelection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selection.appName,
            color = KeepTheme.semanticColors.foreground.neutral,
            style = KeepTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = stringResource(selection.role.reasonRes),
            color = KeepTheme.semanticColors.foreground.muted,
            style = KeepTheme.typography.bodySmall,
        )
    }
}

@get:StringRes
private val SensitiveAppRole.reasonRes: Int
    get() = when (this) {
        SensitiveAppRole.MESSAGING -> R.string.sensitive_block_reason_messaging
        SensitiveAppRole.DIALER -> R.string.sensitive_block_reason_dialer
        SensitiveAppRole.WALLET -> R.string.sensitive_block_reason_wallet
        SensitiveAppRole.SETTINGS -> R.string.sensitive_block_reason_settings
    }
