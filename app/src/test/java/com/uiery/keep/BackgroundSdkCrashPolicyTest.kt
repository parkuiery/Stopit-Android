package com.uiery.keep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSdkCrashPolicyTest {

    @Test
    fun knownGoogleMeasurementAttributionSourceCrashIsContainableOffMainThread() {
        val throwable = NoSuchMethodError(
            "No virtual method getAttributionSource()Landroid/content/AttributionSource; in class Landroid/content/Context; or its super classes",
        ).withStack(
            StackTraceElement(
                "com.google.android.gms.common.api.GoogleApi",
                "<init>",
                "com.google.android.gms:play-services-base@@18.9.0",
                11,
            ),
            StackTraceElement(
                "com.google.android.gms.measurement.internal.AppMeasurementDynamiteService",
                "initializeWithElapsedTime",
                "com.google.android.gms:play-services-measurement-sdk@@23.2.0",
                4,
            ),
        )

        assertTrue(
            shouldContainBackgroundSdkCrash(
                throwable = throwable,
                isMainThread = false,
            ),
        )
    }

    @Test
    fun knownProfileInstallerPackageInfoCrashIsContainableOffMainThread() {
        val throwable = NoSuchMethodError(
            "No static method of(J)Landroid/content/pm/PackageManager\$PackageInfoFlags; in class Landroid/content/pm/PackageManager\$PackageInfoFlags; or its super classes",
        ).withStack(
            StackTraceElement(
                "androidx.profileinstaller.ProfileVerifier\$Api33Impl",
                "getPackageInfo",
                "ProfileVerifier.java",
                1,
            ),
        )

        assertTrue(
            shouldContainBackgroundSdkCrash(
                throwable = throwable,
                isMainThread = false,
            ),
        )
    }

    @Test
    fun knownComposeFontWeightAdjustmentCrashIsContainableOffMainThread() {
        val throwable = NoSuchFieldError(
            "No instance field fontWeightAdjustment of type I in class Landroid/content/res/Configuration; or its superclasses",
        ).withStack(
            StackTraceElement(
                "androidx.compose.ui.text.font.FontWeightAdjustmentHelperApi31",
                "fontWeightAdjustment",
                "AndroidFontResolveInterceptor.android.kt",
                1,
            ),
        )

        assertTrue(
            shouldContainBackgroundSdkCrash(
                throwable = throwable,
                isMainThread = false,
            ),
        )
    }

    @Test
    fun unknownOrMainThreadCrashesStillDelegateToThePlatformHandler() {
        val known = NoSuchMethodError(
            "No virtual method getAttributionSource()Landroid/content/AttributionSource; in class Landroid/content/Context; or its super classes",
        ).withStack(
            StackTraceElement(
                "com.google.android.gms.common.api.GoogleApi",
                "<init>",
                "com.google.android.gms:play-services-base@@18.9.0",
                11,
            ),
        )
        val unknown = IllegalStateException("app bug").withStack(
            StackTraceElement("com.uiery.keep.KeepApplication", "onCreate", "KeepApplication.kt", 17),
        )

        assertFalse(shouldContainBackgroundSdkCrash(throwable = known, isMainThread = true))
        assertFalse(shouldContainBackgroundSdkCrash(throwable = unknown, isMainThread = false))
    }

    @Test
    fun unknownCrashIsRethrownWhenNoPlatformDelegateExists() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val mainThread = Thread.currentThread()
        val backgroundThread = Thread("background-sdk-crash-policy-test")
        val unknown = IllegalStateException("app bug").withStack(
            StackTraceElement("com.uiery.keep.KeepApplication", "onCreate", "KeepApplication.kt", 17),
        )

        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            installBackgroundSdkCrashGuard(mainThread = mainThread)

            val handler = Thread.getDefaultUncaughtExceptionHandler()
                ?: throw AssertionError("Background SDK crash guard was not installed")
            try {
                handler.uncaughtException(backgroundThread, unknown)
            } catch (thrown: Throwable) {
                assertSame(unknown, thrown)
                return
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }

        throw AssertionError("Unknown app crash should not be swallowed when no delegate exists")
    }
}

private fun Throwable.withStack(vararg elements: StackTraceElement): Throwable = apply {
    stackTrace = elements
}
