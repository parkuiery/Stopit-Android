package com.uiery.keep.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.keep.R

@Composable
fun PermissionSettingDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    KeepConfirmationDialog(
        modifier = modifier,
        title = stringResource(R.string.permission_dialog_title),
        message = stringResource(R.string.permission_dialog_message),
        confirmLabel = stringResource(R.string.permission_dialog_confirm),
        dismissLabel = stringResource(R.string.permission_dialog_cancel),
        confirmTone = KeepConfirmationTone.Brand,
        onConfirm = onConfirmation,
        onDismiss = onDismissRequest,
    )
}
