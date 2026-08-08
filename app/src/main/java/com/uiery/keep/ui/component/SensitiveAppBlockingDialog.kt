package com.uiery.keep.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
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
 * "you may miss verification codes" lands, "these are system apps" does not.
 */
@Composable
fun SensitiveAppBlockingDialog(
    selections: List<SensitiveAppSelection>,
    onBlockAll: () -> Unit,
    onExcludeSensitiveApps: () -> Unit,
) {
    if (selections.isEmpty()) return

    // Resolved through map, which is inline; joinToString is not, so it cannot host stringResource.
    val lines = selections.map { selection ->
        stringResource(R.string.sensitive_block_line, selection.appName, stringResource(selection.role.reasonRes))
    }
    val message = lines.joinToString(separator = "\n")

    KeepConfirmationDialog(
        title = stringResource(R.string.sensitive_block_confirm_title),
        message = message,
        confirmLabel = stringResource(R.string.sensitive_block_confirm_include),
        dismissLabel = stringResource(R.string.sensitive_block_confirm_exclude),
        // Blocking them is the consequential branch, and blocking Settings is the hardest of all to
        // undo, so the confirm action does not get to look routine.
        confirmTone = KeepConfirmationTone.Critical,
        onConfirm = onBlockAll,
        onDismiss = onExcludeSensitiveApps,
    )
}

@get:StringRes
private val SensitiveAppRole.reasonRes: Int
    get() = when (this) {
        SensitiveAppRole.MESSAGING -> R.string.sensitive_block_reason_messaging
        SensitiveAppRole.DIALER -> R.string.sensitive_block_reason_dialer
        SensitiveAppRole.WALLET -> R.string.sensitive_block_reason_wallet
        SensitiveAppRole.SETTINGS -> R.string.sensitive_block_reason_settings
    }
