package com.uiery.keep

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.uiery.keep.analytics.acquisition.InstallReferrerAttributionReporter
import com.uiery.keep.data.firstpromise.FirstPromiseStartupRunner
import com.uiery.keep.feature.review.AppLifecycleTracker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun launchKeepApplicationBackgroundWork(
    scope: CoroutineScope,
    checkInstallReferrer: suspend () -> Unit,
    runFirstPromiseStartup: suspend () -> Unit,
) {
    scope.launch { checkInstallReferrer() }
    scope.launch { runFirstPromiseStartup() }
}

@HiltAndroidApp
class KeepApplication : Application() {

    @Inject
    lateinit var appLifecycleTracker: AppLifecycleTracker

    @Inject
    lateinit var installReferrerAttributionReporter: InstallReferrerAttributionReporter

    @Inject
    lateinit var firstPromiseStartupRunner: FirstPromiseStartupRunner

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        installBackgroundSdkCrashGuard()
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleTracker)
        launchKeepApplicationBackgroundWork(
            scope = applicationScope,
            checkInstallReferrer = installReferrerAttributionReporter::checkOnceAfterFirstLaunch,
            runFirstPromiseStartup = firstPromiseStartupRunner::run,
        )
    }
}
