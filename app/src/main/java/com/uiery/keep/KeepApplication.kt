package com.uiery.keep

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.uiery.keep.analytics.acquisition.InstallReferrerAttributionReporter
import com.uiery.keep.feature.review.AppLifecycleTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KeepApplication : Application() {

    @Inject
    lateinit var appLifecycleTracker: AppLifecycleTracker

    @Inject
    lateinit var installReferrerAttributionReporter: InstallReferrerAttributionReporter

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        installBackgroundSdkCrashGuard()
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleTracker)
        applicationScope.launch {
            installReferrerAttributionReporter.checkOnceAfterFirstLaunch()
        }
    }
}
