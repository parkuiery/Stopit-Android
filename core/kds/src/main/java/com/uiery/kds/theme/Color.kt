package com.uiery.kds.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * StopIt palette values arranged with SEED's semantic token structure.
 *
 * Palette values are intentionally internal. Components consume role colors through
 * [KeepSemanticColors], matching SEED's property → role → variant → state model.
 */
internal sealed class KeepColor(
    val gray00: Color,
    val gray100: Color,
    val gray200: Color,
    val gray300: Color,
    val gray400: Color,
    val gray500: Color,
    val gray600: Color,
    val gray700: Color,
    val gray800: Color,
    val gray900: Color,
    val gray1000: Color,
    val brand100: Color,
    val brand200: Color,
    val brand300: Color,
    val brand600: Color,
    val brand700: Color,
    val brand800: Color,
    val red100: Color,
    val red200: Color,
    val red300: Color,
    val red600: Color,
    val red700: Color,
    val red800: Color,
    val red900: Color,
) {
    data object Light : KeepColor(
        gray00 = Color(0xFFFFFFFF),
        gray100 = Color(0xFFF7F8F9),
        gray200 = Color(0xFFF3F4F5),
        gray300 = Color(0xFFEEEFF1),
        gray400 = Color(0xFFDCDEE3),
        gray500 = Color(0xFFD1D3D8),
        gray600 = Color(0xFFB0B3BA),
        gray700 = Color(0xFF868B94),
        gray800 = Color(0xFF555D6D),
        gray900 = Color(0xFF2A3038),
        gray1000 = Color(0xFF1A1C20),
        brand100 = Color(0xFFFFF7E8),
        brand200 = Color(0xFFFFEDCC),
        brand300 = Color(0xFFFFD997),
        brand600 = Color(0xFFFFA927),
        brand700 = Color(0xFFF09300),
        brand800 = Color(0xFFA85D00),
        red100 = Color(0xFFFDF0F0),
        red200 = Color(0xFFFDE7E7),
        red300 = Color(0xFFFED4D2),
        red600 = Color(0xFFFC6A66),
        red700 = Color(0xFFFA342C),
        red800 = Color(0xFFCA1D13),
        red900 = Color(0xFF921708),
    )

    data object Dark : KeepColor(
        gray00 = Color(0xFF000000),
        gray100 = Color(0xFF16171B),
        gray200 = Color(0xFF1D2025),
        gray300 = Color(0xFF2B2E35),
        gray400 = Color(0xFF393D46),
        gray500 = Color(0xFF5B606A),
        gray600 = Color(0xFF868B94),
        gray700 = Color(0xFFB0B3BA),
        gray800 = Color(0xFFDCDEE3),
        gray900 = Color(0xFFE9EAEC),
        gray1000 = Color(0xFFF3F4F5),
        brand100 = Color(0xFF2D2415),
        brand200 = Color(0xFF45351A),
        brand300 = Color(0xFF634A18),
        brand600 = Color(0xFFE69200),
        brand700 = Color(0xFFFFB84A),
        brand800 = Color(0xFFFFD27A),
        red100 = Color(0xFF322323),
        red200 = Color(0xFF4F2624),
        red300 = Color(0xFF742826),
        red600 = Color(0xFFF73526),
        red700 = Color(0xFFFF6E60),
        red800 = Color(0xFFFFA299),
        red900 = Color(0xFFF8C5C3),
    )
}

private val StaticWhite = Color.White
private val StaticBlackAlpha100 = Color(0x07000000)
private val StaticBlackAlpha200 = Color(0x0C000000)
private val StaticBlackAlpha300 = Color(0x10000000)
private val StaticBlackAlpha500 = Color(0x2C000000)
private val StaticBlackAlpha700 = Color(0x74000000)
private val StaticWhiteAlpha50 = Color(0x0DFFFFFF)
private val StaticWhiteAlpha100 = Color(0x17FFFFFF)
private val StaticWhiteAlpha300 = Color(0x2EFFFFFF)

@Immutable
class KeepSemanticColors internal constructor(
    val foreground: KeepForegroundColors,
    val background: KeepBackgroundColors,
    val stroke: KeepStrokeColors,
    val static: KeepStaticColors,
)

@Immutable
class KeepForegroundColors internal constructor(
    val neutral: Color,
    val muted: Color,
    val subtle: Color,
    val placeholder: Color,
    val disabled: Color,
    val inverted: Color,
    val brand: Color,
    val brandContrast: Color,
    val critical: Color,
    val criticalContrast: Color,
    val onBrand: Color,
    val onCritical: Color,
)

@Immutable
class KeepBackgroundColors internal constructor(
    val layerBasement: Color,
    val layerDefault: Color,
    val layerDefaultPressed: Color,
    val layerFloating: Color,
    val layerFloatingPressed: Color,
    val layerSheet: Color,
    val neutralSolid: Color,
    val neutralInverted: Color,
    val neutralInvertedPressed: Color,
    val neutralWeak: Color,
    val neutralWeakPressed: Color,
    val neutralMuted: Color,
    val brandSolid: Color,
    val brandSolidPressed: Color,
    val brandWeak: Color,
    val brandWeakPressed: Color,
    val criticalSolid: Color,
    val criticalSolidPressed: Color,
    val criticalWeak: Color,
    val criticalWeakPressed: Color,
    val disabled: Color,
    val transparent: Color,
    val transparentPressed: Color,
    val transparentSelected: Color,
    val overlay: Color,
    val overlayMuted: Color,
)

@Immutable
class KeepStrokeColors internal constructor(
    val neutralWeak: Color,
    val neutralSubtle: Color,
    val neutralMuted: Color,
    val neutralSolid: Color,
    val neutralContrast: Color,
    val brandSolid: Color,
    val brandWeak: Color,
    val criticalSolid: Color,
    val criticalWeak: Color,
    val focusRing: Color,
) {
    /** Compatibility aliases while app call sites move to SEED role names. */
    val neutralStrong: Color get() = neutralSolid
    val brand: Color get() = brandSolid
    val critical: Color get() = criticalSolid
}

@Immutable
class KeepStaticColors internal constructor(
    val white: Color,
    val whiteAlpha300: Color,
)

private fun createSemanticColors(
    palette: KeepColor,
    darkTheme: Boolean,
): KeepSemanticColors = KeepSemanticColors(
    foreground = KeepForegroundColors(
        neutral = palette.gray1000,
        muted = palette.gray800,
        subtle = palette.gray700,
        placeholder = palette.gray600,
        disabled = palette.gray500,
        inverted = if (darkTheme) palette.gray100 else palette.gray00,
        brand = if (darkTheme) palette.brand700 else palette.brand800,
        brandContrast = palette.brand800,
        critical = palette.red700,
        criticalContrast = palette.red900,
        onBrand = StaticWhite,
        onCritical = StaticWhite,
    ),
    background = KeepBackgroundColors(
        layerBasement = palette.gray200.takeUnless { darkTheme } ?: palette.gray00,
        layerDefault = palette.gray00.takeUnless { darkTheme } ?: palette.gray100,
        layerDefaultPressed = palette.gray100.takeUnless { darkTheme } ?: palette.gray300,
        layerFloating = palette.gray00.takeUnless { darkTheme } ?: palette.gray200,
        layerFloatingPressed = palette.gray100.takeUnless { darkTheme } ?: palette.gray300,
        layerSheet = palette.gray100.takeUnless { darkTheme } ?: palette.gray200,
        neutralSolid = palette.gray1000.takeUnless { darkTheme } ?: palette.gray300,
        neutralInverted = palette.gray900.takeUnless { darkTheme } ?: palette.gray1000,
        neutralInvertedPressed = palette.gray800,
        neutralWeak = palette.gray200.takeUnless { darkTheme } ?: palette.gray300,
        neutralWeakPressed = palette.gray300.takeUnless { darkTheme } ?: palette.gray400,
        neutralMuted = palette.gray400,
        brandSolid = palette.brand600.takeUnless { darkTheme } ?: palette.brand700,
        brandSolidPressed = palette.brand700.takeUnless { darkTheme } ?: palette.brand800,
        brandWeak = palette.brand100,
        brandWeakPressed = palette.brand200,
        criticalSolid = palette.red700.takeUnless { darkTheme } ?: palette.red600,
        criticalSolidPressed = palette.red800.takeUnless { darkTheme } ?: palette.red700,
        criticalWeak = palette.red100,
        criticalWeakPressed = palette.red200,
        disabled = palette.gray200.takeUnless { darkTheme } ?: palette.gray300,
        transparent = Color.Transparent,
        transparentPressed = if (darkTheme) StaticWhiteAlpha50 else StaticBlackAlpha100,
        transparentSelected = if (darkTheme) StaticWhiteAlpha100 else StaticBlackAlpha200,
        overlay = StaticBlackAlpha700,
        overlayMuted = StaticBlackAlpha500,
    ),
    stroke = KeepStrokeColors(
        neutralWeak = palette.gray400,
        neutralSubtle = if (darkTheme) StaticWhiteAlpha50 else StaticBlackAlpha200,
        neutralMuted = if (darkTheme) StaticWhiteAlpha100 else StaticBlackAlpha300,
        neutralSolid = palette.gray800,
        neutralContrast = palette.gray1000,
        brandSolid = if (darkTheme) palette.brand700 else palette.brand800,
        brandWeak = palette.brand300,
        criticalSolid = palette.red700,
        criticalWeak = palette.red300,
        focusRing = if (darkTheme) Color(0xFF1E82EB) else Color(0xFF5E98FE),
    ),
    static = KeepStaticColors(
        white = StaticWhite,
        whiteAlpha300 = StaticWhiteAlpha300,
    ),
)

private val LightSemanticColors = createSemanticColors(
    palette = KeepColor.Light,
    darkTheme = false,
)

private val DarkSemanticColors = createSemanticColors(
    palette = KeepColor.Dark,
    darkTheme = true,
)

internal fun keepLightSemanticColors(): KeepSemanticColors = LightSemanticColors

internal fun keepDarkSemanticColors(): KeepSemanticColors = DarkSemanticColors
