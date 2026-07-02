package com.uiery.keep.util

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Resolves display metadata for a known package name.
 *
 * This is intentionally separate from InstalledAppRepository, which scans selectable apps.
 * Callers that already have a package name can share one fallback contract here instead of
 * duplicating PackageManager lookup rules in Compose UI surfaces.
 */
class AppDisplayMetadataResolver(
    private val packageManager: PackageManager,
) {
    fun resolve(packageName: String): AppDisplayMetadata {
        val appInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()

        val label = appInfo
            ?.let { info -> runCatching { packageManager.getApplicationLabel(info).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: packageName

        val icon = appInfo
            ?.let { info -> runCatching { packageManager.getApplicationIcon(info) }.getOrNull() }
            ?: packageManager.defaultActivityIcon

        return AppDisplayMetadata(
            packageName = packageName,
            label = label,
            icon = icon,
            contentDescription = label,
        )
    }
}

data class AppDisplayMetadata(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val contentDescription: String,
)
