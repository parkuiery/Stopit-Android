package com.uiery.keep

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.uiery.keep.feature.review.AppLifecycleTracker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KeepApplication : Application() {

    @Inject
    lateinit var appLifecycleTracker: AppLifecycleTracker

    override fun onCreate() {
        installBackgroundSdkCrashGuard()
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleTracker)
    }
}
