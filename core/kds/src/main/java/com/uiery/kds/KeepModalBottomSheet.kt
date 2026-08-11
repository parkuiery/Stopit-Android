package com.uiery.kds

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uiery.kds.theme.KeepTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepBottomSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle(
            color = KeepTheme.semanticColors.stroke.neutralWeak,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    dragHandle: @Composable (() -> Unit)? = { KeepBottomSheetDragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.statusBarsPadding(),
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = KeepTheme.semanticColors.background.layerSheet,
        contentColor = KeepTheme.semanticColors.foreground.neutral,
        tonalElevation = 0.dp,
        scrimColor = KeepTheme.semanticColors.background.overlay,
        dragHandle = dragHandle,
        contentWindowInsets = { BottomSheetDefaults.windowInsets },
        content = content,
    )
}
