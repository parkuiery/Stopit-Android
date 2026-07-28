package com.uiery.keep.service

internal val KNOWN_UNINSTALL_PACKAGES = setOf(
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.samsung.android.packageinstaller",
    "com.android.vending",
)

internal fun shouldInterceptUninstallAttempt(
    eventPackageName: String,
    surfaceClassName: String? = null,
    hasApplicationIdMatch: Boolean,
    hasAppNameMatch: Boolean,
    hasEventTextMatch: Boolean = false,
): Boolean {
    if (eventPackageName !in KNOWN_UNINSTALL_PACKAGES) return false
    if (surfaceClassName.isRuntimePermissionGrantSurface()) return false
    return hasApplicationIdMatch || hasAppNameMatch || hasEventTextMatch
}

private fun String?.isRuntimePermissionGrantSurface(): Boolean =
    this?.substringAfterLast('.') == "GrantPermissionsActivity"
