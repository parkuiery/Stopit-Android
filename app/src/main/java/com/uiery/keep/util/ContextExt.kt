package com.uiery.keep.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.uiery.keep.service.KeepAccessibilityService

internal fun enabledAccessibilityServicesContainsService(
    enabledServices: String?,
    serviceComponent: String,
): Boolean {
    if (enabledServices.isNullOrBlank()) return false

    val expectedComponent = normalizeAccessibilityServiceComponent(serviceComponent) ?: return false

    return enabledServices
        .split(':')
        .map { it.trim() }
        .mapNotNull(::normalizeAccessibilityServiceComponent)
        .any { it == expectedComponent }
}

private fun normalizeAccessibilityServiceComponent(component: String): String? {
    if (component.isBlank()) return null

    val separatorIndex = component.indexOf('/')
    if (separatorIndex <= 0 || separatorIndex == component.lastIndex) return null

    val packageName = component.substring(0, separatorIndex)
    val className = component.substring(separatorIndex + 1)
    if (packageName.isBlank() || className.isBlank()) return null

    val normalizedClassName = if (className.startsWith('.')) {
        "$packageName$className"
    } else {
        className
    }

    return "$packageName/$normalizedClassName"
}

fun hasAccessibilityPermission(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    val keepServiceComponent = ComponentName(context, KeepAccessibilityService::class.java).flattenToString()
    return enabledAccessibilityServicesContainsService(
        enabledServices = enabledServices,
        serviceComponent = keepServiceComponent,
    )
}

fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}