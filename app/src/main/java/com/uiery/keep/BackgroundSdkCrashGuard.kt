package com.uiery.keep

private val containableSdkCrashClassPrefixes = listOf(
    "com.google.android.gms.",
    "androidx.profileinstaller.",
    "androidx.compose.ui.text.font.",
)

private val containableSdkCrashMessageFragments = listOf(
    "getAttributionSource()Landroid/content/AttributionSource;",
    "PackageManager\$PackageInfoFlags",
    "fontWeightAdjustment",
)

internal fun shouldContainBackgroundSdkCrash(
    throwable: Throwable,
    isMainThread: Boolean,
): Boolean {
    if (isMainThread) return false
    if (throwable !is NoSuchMethodError &&
        throwable !is NoSuchFieldError &&
        throwable !is ClassNotFoundException
    ) {
        return false
    }

    val message = throwable.message.orEmpty()
    if (containableSdkCrashMessageFragments.none { message.contains(it) }) {
        return false
    }

    return throwable.stackTrace.any { element ->
        containableSdkCrashClassPrefixes.any { prefix ->
            element.className.startsWith(prefix)
        }
    }
}

internal fun installBackgroundSdkCrashGuard(
    mainThread: Thread = android.os.Looper.getMainLooper().thread,
) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (previousHandler is BackgroundSdkCrashGuardHandler) return

    Thread.setDefaultUncaughtExceptionHandler(
        BackgroundSdkCrashGuardHandler(
            mainThread = mainThread,
            delegate = previousHandler,
        ),
    )
}

private class BackgroundSdkCrashGuardHandler(
    private val mainThread: Thread,
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (shouldContainBackgroundSdkCrash(throwable = throwable, isMainThread = thread == mainThread)) {
            return
        }
        delegate?.uncaughtException(thread, throwable) ?: throw throwable
    }
}
