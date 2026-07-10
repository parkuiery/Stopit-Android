package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockElapsedDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockEndedEarlyReason
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

internal const val GOAL_LOCK_ID_ARG = "goalLockId"

@HiltViewModel
internal class GoalLockDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val goalLockRepository: GoalLockRepository,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<GoalLockDetailUiState, GoalLockDetailSideEffect> {
        private val goalLockId: Long = checkNotNull(savedStateHandle[GOAL_LOCK_ID_ARG])
        private var lastLoadDate: LocalDate = LocalDate.now()

        override val container: Container<GoalLockDetailUiState, GoalLockDetailSideEffect> =
            container(GoalLockDetailUiState())

        init {
            analytics.logScreenView(KeepAnalyticsScreen.GOAL_LOCK_DETAIL)
        }

        fun loadGoalLock(
            today: LocalDate = LocalDate.now(),
            force: Boolean = false,
        ) = intent {
            if (!force && state.goalLock != null) return@intent
            lastLoadDate = today
            reduce { state.copy(isLoading = true, error = null, showEndConfirmation = false) }
            try {
                val goalLock = goalLockRepository.fetch(goalLockId)
                if (goalLock == null) {
                    reduce {
                        state.copy(
                            isLoading = false,
                            goalLock = null,
                            presentation = null,
                        )
                    }
                    postSideEffect(GoalLockDetailSideEffect.NotFound)
                    return@intent
                }
                val runtimeStatus = GoalLockPolicy.runtimeStatus(goalLock, today.atStartOfDay())
                if (goalLock.status == GoalLockStoredStatus.Active &&
                    runtimeStatus == GoalLockRuntimeStatus.Completed
                ) {
                    val completed = goalLock.copy(status = GoalLockStoredStatus.Completed)
                    val persisted = goalLockRepository.updateIfActive(completed)
                    if (persisted) {
                        ignoreDetailAnalyticsFailure {
                            analytics.trackGoalLockCompleted(
                                lockMode = goalLock.lockMode.analyticsLockMode,
                                durationDaysBucket = goalLockDurationDaysBucket(
                                    goalLock.startDate,
                                    goalLock.endDate,
                                ),
                            )
                        }
                    }
                    reduce {
                        state.copy(
                            isLoading = false,
                            goalLock = completed,
                            presentation = goalLockDetailPresentation(completed, today),
                            error = if (persisted) null else GoalLockDetailError.Load,
                        )
                    }
                    return@intent
                }
                reduce {
                    state.copy(
                        isLoading = false,
                        goalLock = goalLock,
                        presentation = goalLockDetailPresentation(goalLock, today),
                        error = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                reduce { state.copy(isLoading = false, error = GoalLockDetailError.Load) }
            }
        }

        fun retryLoad() = loadGoalLock(today = lastLoadDate, force = true)

        fun refreshAfterEdit(today: LocalDate = LocalDate.now()) =
            loadGoalLock(today = today, force = true)

        fun requestEndGoalLock() =
            intent {
                if (state.presentation?.canEnd != true) return@intent
                reduce { state.copy(showEndConfirmation = true) }
            }

        fun cancelEndGoalLock() =
            intent {
                reduce { state.copy(showEndConfirmation = false) }
            }

        fun confirmEndGoalLock(today: LocalDate = LocalDate.now()) =
            intent {
                val current = state.goalLock ?: return@intent
                if (state.presentation?.canEnd != true) return@intent
                val ended = current.copy(status = GoalLockStoredStatus.EndedEarly)
                try {
                    if (!goalLockRepository.updateIfActive(ended)) {
                        reduce {
                            state.copy(
                                showEndConfirmation = false,
                                error = GoalLockDetailError.End,
                            )
                        }
                        return@intent
                    }
                    ignoreDetailAnalyticsFailure {
                        analytics.trackGoalLockEndedEarly(
                            lockMode = current.lockMode.analyticsLockMode,
                            elapsedDaysBucket = elapsedDaysBucket(current.startDate, today),
                            reason = AnalyticsGoalLockEndedEarlyReason.USER_CONFIRMED,
                        )
                    }
                    reduce {
                        state.copy(
                            goalLock = ended,
                            presentation = goalLockDetailPresentation(ended, today),
                            showEndConfirmation = false,
                            error = null,
                        )
                    }
                    postSideEffect(GoalLockDetailSideEffect.Ended)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    reduce {
                        state.copy(
                            showEndConfirmation = false,
                            error = GoalLockDetailError.End,
                        )
                    }
                }
            }
    }

internal data class GoalLockDetailUiState(
    val isLoading: Boolean = true,
    val goalLock: GoalLock? = null,
    val presentation: GoalLockDetailPresentation? = null,
    val showEndConfirmation: Boolean = false,
    val error: GoalLockDetailError? = null,
) {
    val goalName: String get() = goalLock?.goalName.orEmpty()
    val selectedAppCount: Int get() = goalLock?.selectedPackages?.size ?: 0
    val canEdit: Boolean
        get() = !isLoading && error != GoalLockDetailError.Load && presentation?.canEdit == true
    val canEnd: Boolean
        get() = !isLoading && error != GoalLockDetailError.Load && presentation?.canEnd == true
}

internal enum class GoalLockDetailError {
    Load,
    End,
}

internal sealed interface GoalLockDetailSideEffect {
    data object NotFound : GoalLockDetailSideEffect
    data object Ended : GoalLockDetailSideEffect
}

private fun elapsedDaysBucket(
    startDate: LocalDate,
    today: LocalDate,
): String = when (ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0)) {
    0L -> AnalyticsGoalLockElapsedDaysBucket.ZERO
    in 1L..2L -> AnalyticsGoalLockElapsedDaysBucket.ONE_TO_TWO
    in 3L..6L -> AnalyticsGoalLockElapsedDaysBucket.THREE_TO_SIX
    in 7L..14L -> AnalyticsGoalLockElapsedDaysBucket.SEVEN_TO_FOURTEEN
    else -> AnalyticsGoalLockElapsedDaysBucket.FIFTEEN_PLUS
}

private inline fun ignoreDetailAnalyticsFailure(block: () -> Unit) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Persistence is authoritative; analytics cannot change the user-visible result.
    }
}
