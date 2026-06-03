package com.uiery.keep.feature.routine

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun createExactAlarmSettingsIntent(
    packageName: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Intent? {
    if (sdkInt < Build.VERSION_CODES.S) {
        return null
    }

    return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }
}
