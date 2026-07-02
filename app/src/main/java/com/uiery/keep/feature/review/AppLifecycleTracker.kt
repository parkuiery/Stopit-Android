package com.uiery.keep.feature.review

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.uiery.keep.datastore.ReviewPromptStateStore
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppLifecycleTracker @Inject constructor(
    private val reviewPromptStateStore: ReviewPromptStateStore,
    private val clock: Clock,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            reviewPromptStateStore.recordBackgrounded(clock.millis())
        }
    }
}
