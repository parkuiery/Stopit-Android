package com.uiery.keep

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.uiery.keep.analytics.acquisition.InstallReferrerAttributionReporter
import com.uiery.keep.data.firstpromise.FirstPromiseStartupRunner
import com.uiery.keep.feature.review.AppLifecycleTracker
import com.uiery.keep.websiteblocking.WebsiteBlockingStatusReporter
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

    @Inject
    lateinit var websiteBlockingStatusReporter: WebsiteBlockingStatusReporter

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        installBackgroundSdkCrashGuard()
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleTracker)
        // 프로세스 수명에 묶는다. 웹 차단이 내려앉는 순간은 화면이 떠 있을 때가 아니라
        // 루틴이 백그라운드에서 도는 동안인 경우가 많다. 수신자나 서비스로 프로세스가
        // 깨어나도 Application.onCreate 는 실행되므로 그 회차도 관측된다.
        websiteBlockingStatusReporter.start(applicationScope)
        launchKeepApplicationBackgroundWork(
            scope = applicationScope,
            checkInstallReferrer = installReferrerAttributionReporter::checkOnceAfterFirstLaunch,
            runFirstPromiseStartup = firstPromiseStartupRunner::run,
        )
    }
}
