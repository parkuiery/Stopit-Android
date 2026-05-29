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

    return enabledServices
        .split(':')
        .map { it.trim() }
        .any { it == serviceComponent }
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