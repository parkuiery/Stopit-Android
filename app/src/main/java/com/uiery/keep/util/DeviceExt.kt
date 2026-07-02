package com.uiery.keep.util

import android.content.Context
import android.provider.Settings

fun deviceId(context: Context): String =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "unknown"
