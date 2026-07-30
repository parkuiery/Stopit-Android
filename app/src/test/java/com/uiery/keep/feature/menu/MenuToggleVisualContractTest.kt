package com.uiery.keep.feature.menu

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuToggleVisualContractTest {

    @Test
    fun uninstallProtectionCopyUsesReadableSemanticHierarchy() {
        val source = File(
            "src/main/java/com/uiery/keep/feature/menu/component/MenuToggleItem.kt",
        ).readText()

        assertTrue(source.contains("semanticColors.foreground.neutral"))
        assertTrue(source.contains("semanticColors.foreground.muted"))
        assertFalse(source.contains("colors.onTertiaryContainer"))
    }
}
