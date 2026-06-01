package com.uiery.keep.util

import android.util.Log
import com.uiery.keep.BuildConfig

/**
 * Central app logging boundary.
 *
 * Production source must not call android.util.Log directly. Keep logcat output debug-only here
 * so sensitive runtime values cannot be reintroduced from feature or entry-point code.
 */
object AppLogger {
    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return

        Log.d(tag, message, throwable)
    }
}
