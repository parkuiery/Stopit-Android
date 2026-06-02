package com.uiery.keep.feature.splash

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.ManualLockTimePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay

import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val blockingStateStore: BlockingStateStore,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<SplashUiState, SplashSideEffect> {
        override val container: Container<SplashUiState, SplashSideEffect> = container(SplashUiState())

        init {
            analytics.logScreenView(KeepAnalyticsScreen.SPLASH)
            navigateScreen()
        }

        private fun navigateScreen() =
            intent {
                delay(0.7.seconds)
                postSideEffect(handleNavigate())
            }

        private suspend fun handleNavigate(): SplashSideEffect {
            if (getIsNew()) {
                trackFirstOpenIfNeeded()
                return SplashSideEffect.MoveToOnboarding
            }

            val lockTime = getLockTime()
            val isLock = ManualLockTimePolicy.isActiveAt(lockTime)
            return when {
                isLock && lockTime != null -> SplashSideEffect.MoveToLock(lockTime = lockTime, false)
                else -> SplashSideEffect.MoveToHome
            }
        }

        private suspend fun trackFirstOpenIfNeeded() {
            if (blockingStateStore.markFirstOpenTrackedIfNeeded(System.currentTimeMillis())) {
                analytics.trackFirstOpen()
            }
        }

        private suspend fun getIsNew(): Boolean = blockingStateStore.readIsNew(default = true)

        private suspend fun getLockTime(): String? = blockingStateStore.readLockTime()
    }

data class SplashUiState(
    val isNew: Boolean = false,
    val isLock: Boolean = false,
    val lockTime: String = LocalDateTime.now().toString(),
)

sealed class SplashSideEffect {
    data object MoveToHome : SplashSideEffect()

    data object MoveToOnboarding : SplashSideEffect()

    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : SplashSideEffect()
}
