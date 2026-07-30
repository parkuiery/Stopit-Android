package com.uiery.keep.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryButtonVisualContractTest {

    @Test
    fun categoryButtonUsesNeutralMutedKdsCardInsteadOfBrandSurface() {
        val source = File(
            "src/main/java/com/uiery/keep/ui/component/CategoryButton.kt",
        ).readText()

        assertTrue(source.contains("KeepCard("))
        assertTrue(source.contains("KeepCardVariant.NeutralMuted"))
        assertTrue(source.contains("Image("))
        assertTrue(source.contains("R.drawable.shield"))
        assertTrue(source.contains("foreground.subtle"))
        assertFalse(source.contains("KeepCardVariant.BrandSolid"))
        assertFalse(source.contains("KeepTheme.colors.tertiary"))
    }
}
