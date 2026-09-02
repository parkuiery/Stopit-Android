package com.uiery.keep.feature.splash

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.setRoutinesCount
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.FirstPromiseStateReadResult
import com.uiery.keep.datastore.ManualLockTimePolicy
import com.uiery.keep.data.routine.RoutineRestoreAftercare
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.pomodoro.PomodoroBlockContextSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay

import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val blockingStateStore: BlockingStateStore,
        private val firstPromiseDraftStore: FirstPromiseDraftStore,
        private val analytics: KeepAnalytics,
        private val routineRestoreAftercare: RoutineRestoreAftercare,
        private val pomodoroBlockContextSource: PomodoroBlockContextSource,
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
                val restoreResult = routineRestoreAftercare.rescheduleRestoredEnabledRoutinesFromRoom()
                analytics.setRoutinesCount(restoreResult.routines.size)
                postSideEffect(handleNavigate(hasRestoredRoomData = restoreResult.routines.isNotEmpty()))
            }

        private suspend fun handleNavigate(hasRestoredRoomData: Boolean): SplashSideEffect {
            val isNew = getIsNew()
            if (isNew && hasIncompletePromiseCoachAssignment()) {
                trackFirstOpenIfNeeded()
                return SplashSideEffect.MoveToOnboarding
            }
            if (!hasRestoredRoomData && isNew) {
                trackFirstOpenIfNeeded()
                return SplashSideEffect.MoveToOnboarding
            }

            // 집중 세션은 잠금 관점에서 타이머 잠금 하나이므로 `LOCK_TIME` 이 서 있다. 그것만 보고
            // 잠금 화면으로 보내면 사용자는 페이즈도 사이클도 없는 화면에 갇히고, 세션을 끝낼
            // 길도 사라진다. 세션이 살아 있으면 세션 화면이 그 자리다.
            if (pomodoroBlockContextSource.blockContext(Instant.now()) != null) {
                return SplashSideEffect.MoveToPomodoro
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

        private suspend fun hasIncompletePromiseCoachAssignment(): Boolean {
            val state = (firstPromiseDraftStore.readStateResult() as? FirstPromiseStateReadResult.Available)
                ?.state ?: return false
            return state.assignment == OnboardingVariant.PromiseCoachV1 &&
                state.phase !in setOf(
                    FirstPromisePhase.CompletedEnabled,
                    FirstPromisePhase.CompletedDisabled,
                )
        }

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

    /** 집중 세션이 도는 중. 잠금 화면이 아니라 세션 화면이 사용자가 있어야 할 자리다. */
    data object MoveToPomodoro : SplashSideEffect()
}
