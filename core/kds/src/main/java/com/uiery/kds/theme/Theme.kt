package com.uiery.kds.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LegacyDarkColorScheme = darkColorScheme(
    primary = KeepColor.Dark.brand700,
    onPrimary = KeepColor.Light.gray00,
    primaryContainer = KeepColor.Dark.brand100,
    onPrimaryContainer = KeepColor.Dark.brand700,
    background = KeepColor.Dark.gray00,
    onBackground = KeepColor.Dark.gray1000,
    error = KeepColor.Dark.red600,
    secondary = KeepColor.Dark.gray100,
    onSecondary = KeepColor.Dark.gray200,
    tertiary = KeepColor.Dark.gray300,
    onTertiary = KeepColor.Dark.gray400,
    tertiaryContainer = KeepColor.Dark.gray500,
    onTertiaryContainer = KeepColor.Dark.gray600,
    surface = KeepColor.Dark.gray700,
    onSurface = KeepColor.Dark.gray800,
    surfaceVariant = KeepColor.Dark.gray900,
    onSurfaceVariant = KeepColor.Dark.gray1000,
)

private val LegacyLightColorScheme = lightColorScheme(
    primary = KeepColor.Light.brand600,
    onPrimary = KeepColor.Light.gray00,
    primaryContainer = KeepColor.Light.brand100,
    onPrimaryContainer = KeepColor.Light.brand800,
    background = KeepColor.Light.gray200,
    onBackground = KeepColor.Light.gray1000,
    error = KeepColor.Light.red700,
    secondary = KeepColor.Light.gray100,
    onSecondary = KeepColor.Light.gray00,
    tertiary = KeepColor.Light.gray200,
    onTertiary = KeepColor.Light.gray300,
    tertiaryContainer = KeepColor.Light.gray400,
    onTertiaryContainer = KeepColor.Light.gray500,
    surface = KeepColor.Light.gray700,
    onSurface = KeepColor.Light.gray800,
    surfaceVariant = KeepColor.Light.gray900,
    onSurfaceVariant = KeepColor.Light.gray1000,
)

private val LightMaterialColorScheme = createLightMaterialColorScheme()

internal fun keepLightMaterialColorScheme(): ColorScheme = LightMaterialColorScheme

private fun createLightMaterialColorScheme(): ColorScheme {
    val colors = keepLightSemanticColors()
    return lightColorScheme(
        primary = colors.background.brandSolid,
        onPrimary = colors.foreground.onBrand,
        primaryContainer = colors.background.brandWeak,
        onPrimaryContainer = colors.foreground.brand,
        secondary = colors.foreground.neutral,
        onSecondary = colors.foreground.inverted,
        secondaryContainer = colors.background.neutralWeak,
        onSecondaryContainer = colors.foreground.neutral,
        tertiary = colors.foreground.brand,
        onTertiary = colors.foreground.inverted,
        background = colors.background.layerBasement,
        onBackground = colors.foreground.neutral,
        surface = colors.background.layerDefault,
        onSurface = colors.foreground.neutral,
        surfaceVariant = colors.background.neutralWeak,
        onSurfaceVariant = colors.foreground.muted,
        outline = colors.stroke.neutralStrong,
        outlineVariant = colors.stroke.neutralWeak,
        error = colors.background.criticalSolid,
        onError = colors.foreground.onCritical,
        errorContainer = colors.background.criticalWeak,
        onErrorContainer = colors.foreground.critical,
        inverseSurface = colors.foreground.neutral,
        inverseOnSurface = colors.foreground.inverted,
        inversePrimary = colors.background.brandSolid,
        scrim = colors.background.overlay,
    )
}

private val DarkMaterialColorScheme = createDarkMaterialColorScheme()

internal fun keepDarkMaterialColorScheme(): ColorScheme = DarkMaterialColorScheme

private fun createDarkMaterialColorScheme(): ColorScheme {
    val colors = keepDarkSemanticColors()
    return darkColorScheme(
        primary = colors.background.brandSolid,
        onPrimary = colors.foreground.onBrand,
        primaryContainer = colors.background.brandWeak,
        onPrimaryContainer = colors.foreground.brand,
        secondary = colors.foreground.neutral,
        onSecondary = colors.foreground.inverted,
        secondaryContainer = colors.background.neutralWeak,
        onSecondaryContainer = colors.foreground.neutral,
        tertiary = colors.foreground.brand,
        onTertiary = colors.foreground.inverted,
        background = colors.background.layerBasement,
        onBackground = colors.foreground.neutral,
        surface = colors.background.layerDefault,
        onSurface = colors.foreground.neutral,
        surfaceVariant = colors.background.neutralWeak,
        onSurfaceVariant = colors.foreground.muted,
        outline = colors.stroke.neutralStrong,
        outlineVariant = colors.stroke.neutralWeak,
        error = colors.background.criticalSolid,
        onError = colors.foreground.onCritical,
        errorContainer = colors.background.criticalWeak,
        onErrorContainer = colors.foreground.critical,
        inverseSurface = colors.foreground.neutral,
        inverseOnSurface = colors.foreground.inverted,
        inversePrimary = colors.background.brandSolid,
        scrim = colors.background.overlay,
    )
}

@Composable
fun KeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val legacyColors = if (darkTheme) {
        LegacyDarkColorScheme
    } else {
        LegacyLightColorScheme
    }
    val semanticColors = if (darkTheme) {
        keepDarkSemanticColors()
    } else {
        keepLightSemanticColors()
    }
    val materialColors = if (darkTheme) {
        keepDarkMaterialColorScheme()
    } else {
        keepLightMaterialColorScheme()
    }

    MaterialTheme(
        colorScheme = materialColors,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalColors provides legacyColors,
            LocalSemanticColors provides semanticColors,
        ) {
            content()
        }
    }

}

object KeepTheme {
    /**
     * Legacy Material slots used by existing screens.
     *
     * New KDS components should use [semanticColors] so the property name expresses intent.
     */
    val colors
        @Composable get() = LocalColors.current

    val semanticColors
        @Composable get() = LocalSemanticColors.current
}

val LocalColors = staticCompositionLocalOf { LegacyLightColorScheme }

val LocalSemanticColors = staticCompositionLocalOf { keepLightSemanticColors() }
