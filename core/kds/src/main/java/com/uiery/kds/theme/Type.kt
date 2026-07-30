package com.uiery.kds.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SEED uses each platform's system font to preserve native rendering and accessibility.
 *
 * The old symbol is retained for source compatibility, but now resolves to Android's
 * system font rather than a bundled brand font.
 */
val PretendardFontFamily: FontFamily = FontFamily.Default

private val defaultTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    letterSpacing = 0.sp,
)

/**
 * Material roles mapped onto SEED's t1–t14 font-size and line-height scale.
 */
val Typography = Typography(
    displayLarge = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 60.sp,
    ),
    displayMedium = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 35.sp,
    ),
    headlineSmall = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = defaultTextStyle.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = defaultTextStyle.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = defaultTextStyle.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = defaultTextStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)
