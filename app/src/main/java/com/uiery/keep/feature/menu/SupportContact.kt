package com.uiery.keep.feature.menu

internal const val STOPIT_SUPPORT_EMAIL = "parkuiery@gmail.com"

object SupportContactSurface {
    const val MENU = "menu"
}

object SupportContactFallbackType {
    const val CLIPBOARD = "clipboard"
}

internal fun buildSupportContactDiagnostics(
    versionName: String,
    androidRelease: String,
    sdkInt: Int,
    deviceModel: String,
): String = "Version $versionName\nAndroid OS $androidRelease ($sdkInt),$deviceModel"

internal fun buildSupportContactFallbackText(
    supportEmail: String,
    diagnostics: String,
): String = """
Support email: $supportEmail

Please include the diagnostics below when contacting StopIt support.

-
$diagnostics
""".trimIndent()
