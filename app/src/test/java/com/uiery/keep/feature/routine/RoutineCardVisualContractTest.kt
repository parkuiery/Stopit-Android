package com.uiery.keep.feature.routine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCardVisualContractTest {

    @Test
    fun routineRowsUseLayerDefaultKdsCardsInsteadOfBasementColoredContainers() {
        val source = File(
            "src/main/java/com/uiery/keep/feature/routine/component/RoutineListContent.kt",
        ).readText()

        assertTrue(source.contains("KeepCard("))
        assertTrue(source.contains("KeepCardVariant.LayerDefault"))
        assertTrue(source.contains("bordered = true"))
        assertFalse(source.contains("KeepTheme.colors.tertiary"))
        assertFalse(source.contains(".background("))
        assertFalse(source.contains(".clickable"))
    }

    @Test
    fun routineEditorUsesSeedFormAndBottomSheetStructure() {
        val source = File(
            "src/main/java/com/uiery/keep/feature/routine/component/RoutineBottomSheetContent.kt",
        ).readText()

        assertTrue(source.contains("RoutineSheetHeader("))
        assertTrue(source.contains("RoutineSheetFooter("))
        assertTrue(source.contains(".verticalScroll("))
        assertTrue(source.contains("KeepField("))
        assertTrue(source.contains("KeepInputButton("))
        assertTrue(source.contains("KeepChip("))
        assertTrue(source.contains("var pendingTime by remember(time)"))
        assertTrue(source.contains("onClick = { onConfirm(pendingTime) }"))
        assertTrue(source.contains("RoutineOverflowMenu("))
        assertTrue(source.contains("KeepMenu("))
        assertTrue(source.contains("KeepMenuItem("))
        assertTrue(source.contains("KeepConfirmationDialog("))
        assertTrue(source.contains("KeepConfirmationTone.Critical"))
        assertTrue(source.contains("KeepMenuItemTone.Critical"))
        assertTrue(source.contains("helperTextTone = KeepFieldHelperTone.Muted"))
        assertFalse(source.contains("RoutineDeleteSection("))
        assertFalse(source.contains("KeepTextButtonVariant.Critical"))
        assertFalse(
            source.substringAfter("private fun RoutineSheetHeader(")
                .substringBefore("private fun RoutineOverflowMenu(")
                .contains("outline_delete_24"),
        )
        assertFalse(source.contains("RoutineSettingCard("))
        assertFalse(source.contains("RoutineTimeButton("))
    }
}
