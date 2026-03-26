package com.uiery.kds.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColorScheme = darkColorScheme(
    primary = KeepColor.Dark.orange400,
    background = KeepColor.Dark.background,
    onBackground = KeepColor.Dark.dimmedBackground,
    error = KeepColor.Dark.red500,
    secondary = KeepColor.Dark.gray50,
    onSecondary = KeepColor.Dark.gray100,
    tertiary = KeepColor.Dark.gray200,
    onTertiary = KeepColor.Dark.gray300,
    tertiaryContainer = KeepColor.Dark.gray400,
    onTertiaryContainer = KeepColor.Dark.gray500,
    surface = KeepColor.Dark.gray600,
    onSurface = KeepColor.Dark.gray700,
    surfaceVariant = KeepColor.Dark.gray800,
    onSurfaceVariant = KeepColor.Dark.gray900,
)

private val LightColorScheme = lightColorScheme(
    primary = KeepColor.Light.orange400,
    background = KeepColor.Light.background,
    onBackground = KeepColor.Light.dimmedBackground,
    error = KeepColor.Light.red500,
    secondary = KeepColor.Light.gray50,
    onSecondary = KeepColor.Light.gray100,
    tertiary = KeepColor.Light.gray200,
    onTertiary = KeepColor.Light.gray300,
    tertiaryContainer = KeepColor.Light.gray400,
    onTertiaryContainer = KeepColor.Light.gray500,
    surface = KeepColor.Light.gray600,
    onSurface = KeepColor.Light.gray700,
    surfaceVariant = KeepColor.Light.gray800,
    onSurfaceVariant = KeepColor.Light.gray900,
)

@Composable
fun KeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(typography = Typography) {
        CompositionLocalProvider(LocalColors provides colorScheme) {
            content()
        }
    }

}

object KeepTheme {
    val colors
        @Composable get() = LocalColors.current
}

val LocalColors = staticCompositionLocalOf { lightColorScheme() }
