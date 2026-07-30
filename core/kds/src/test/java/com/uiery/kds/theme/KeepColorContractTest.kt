package com.uiery.kds.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepColorContractTest {

    @Test
    fun `brand action colors match StopIt in light and dark themes`() {
        val light = keepLightSemanticColors()
        val dark = keepDarkSemanticColors()

        assertEquals(Color(0xFFFFA927), light.background.brandSolid)
        assertEquals(Color(0xFFF09300), light.background.brandSolidPressed)
        assertEquals(Color(0xFFFFB84A), dark.background.brandSolid)
        assertEquals(Color(0xFFFFD27A), dark.background.brandSolidPressed)
        assertEquals(Color(0xFFA85D00), light.foreground.brand)
        assertEquals(Color(0xFFFFB84A), dark.foreground.brand)
        assertEquals(Color.White, light.foreground.onBrand)
        assertEquals(Color.White, dark.foreground.onBrand)
    }

    @Test
    fun `material color scheme uses the same semantic action colors`() {
        val semanticColors = keepLightSemanticColors()
        val materialColors = keepLightMaterialColorScheme()

        assertEquals(semanticColors.background.brandSolid, materialColors.primary)
        assertEquals(semanticColors.foreground.onBrand, materialColors.onPrimary)
        assertEquals(semanticColors.background.layerBasement, materialColors.background)
        assertEquals(semanticColors.foreground.neutral, materialColors.onBackground)
    }

    @Test
    fun `dark material color scheme preserves readable surface roles`() {
        val semanticColors = keepDarkSemanticColors()
        val materialColors = keepDarkMaterialColorScheme()

        assertEquals(semanticColors.background.layerDefault, materialColors.surface)
        assertEquals(semanticColors.foreground.neutral, materialColors.onSurface)
        assertEquals(Color(0xFF16171B), semanticColors.background.layerDefault)
        assertEquals(Color(0xFFF3F4F5), semanticColors.foreground.neutral)
    }

    @Test
    fun `neutral muted card remains distinct from the screen basement`() {
        val light = keepLightSemanticColors()
        val dark = keepDarkSemanticColors()

        assertEquals(Color(0xFFDCDEE3), light.background.neutralMuted)
        assertEquals(Color(0xFF393D46), dark.background.neutralMuted)
    }

    @Test
    fun `bottom sheet uses a calm surface distinct from floating dialogs`() {
        val light = keepLightSemanticColors()
        val dark = keepDarkSemanticColors()

        assertEquals(Color(0xFFF7F8F9), light.background.layerSheet)
        assertEquals(Color(0xFFFFFFFF), light.background.layerFloating)
        assertEquals(Color(0xFF1D2025), dark.background.layerSheet)
        assertEquals(Color(0xFF1D2025), dark.background.layerFloating)
    }
}
