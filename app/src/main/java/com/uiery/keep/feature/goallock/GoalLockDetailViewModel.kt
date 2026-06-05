package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockDurationDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockElapsedDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockEndedEarlyReason
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val goalLockDao: GoalLockDao,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<GoalLockDetailUiState, GoalLockDetailSideEffect> {
        private val goalLockId: Long = checkNotNull(savedStateHandle[GOAL_LOCK_ID_ARG]) {
            "Goal lock id is required"
        }

        override val container: Container<GoalLockDetailUiState, GoalLockDetailSideEffect> =
            container(GoalLockDetailUiState())

        fun loadGoalLock(today: LocalDate = LocalDate.now()) =
            intent {
                val goalLock = goalLockDao.fetch(goalLockId)?.toDomain()
                if (goalLock == null) {
                    postSideEffect(GoalLockDetailSideEffect.NotFound)
                    return@intent
                }

                val normalizedGoalLock = completeIfExpired(goalLock = goalLock, today = today)
                reduce {
                    state.copy(
                        goalLock = normalizedGoalLock,
                        showEndConfirmation = false,
                        isEnded = normalizedGoalLock.status == GoalLockStoredStatus.EndedEarly,
                        isCompleted = normalizedGoalLock.status == GoalLockStoredStatus.Completed,
                    )
                }
            }

        fun requestEndGoalLock() =
            intent {
                if (state.goalLock == null || state.isEnded || state.isCompleted) return@intent
                reduce { state.copy(showEndConfirmation = true) }
            }

        fun cancelEndGoalLock() =
            intent {
                reduce { state.copy(showEndConfirmation = false) }
            }

        fun confirmEndGoalLock(today: LocalDate = LocalDate.now()) =
            intent {
                val current = state.goalLock ?: return@intent
                if (current.status == GoalLockStoredStatus.EndedEarly) {
                    reduce { state.copy(showEndConfirmation = false, isEnded = true) }
                    return@intent
                }
                if (current.status == GoalLockStoredStatus.Completed) {
                    reduce { state.copy(showEndConfirmation = false, isCompleted = true) }
                    return@intent
                }

                val ended = current.copy(status = GoalLockStoredStatus.EndedEarly)
                goalLockDao.update(GoalLockEntity.fromDomain(ended))
                analytics.trackGoalLockEndedEarly(
                    lockMode = current.lockMode.analyticsLockMode,
                    elapsedDaysBucket = elapsedDaysBucket(current.startDate, today),
                    reason = AnalyticsGoalLockEndedEarlyReason.USER_CONFIRMED,
                )
                reduce {
                    state.copy(
                        goalLock = ended,
                        showEndConfirmation = false,
                        isEnded = true,
                        isCompleted = false,
                    )
                }
                postSideEffect(GoalLockDetailSideEffect.Ended)
            }

        private fun completeIfExpired(
            goalLock: GoalLock,
            today: LocalDate,
        ): GoalLock {
            if (goalLock.status != GoalLockStoredStatus.Active) return goalLock
            if (GoalLockPolicy.runtimeStatus(goalLock, today.atStartOfDay()) != GoalLockRuntimeStatus.Completed) {
                return goalLock
            }

            val completed = goalLock.copy(status = GoalLockStoredStatus.Completed)
            goalLockDao.update(GoalLockEntity.fromDomain(completed))
            analytics.trackGoalLockCompleted(
                lockMode = goalLock.lockMode.analyticsLockMode,
                durationDaysBucket = durationDaysBucket(goalLock.startDate, goalLock.endDate),
            )
            return completed
        }
    }

internal data class GoalLockDetailUiState(
    val goalLock: GoalLock? = null,
    val showEndConfirmation: Boolean = false,
    val isEnded: Boolean = false,
    val isCompleted: Boolean = false,
) {
    val goalName: String = goalLock?.goalName.orEmpty()
    val lockModeLabel: String = goalLock?.lockMode?.detailLabel.orEmpty()
    val selectedAppCount: Int = goalLock?.selectedPackages?.size ?: 0
}

internal sealed interface GoalLockDetailSideEffect {
    data object NotFound : GoalLockDetailSideEffect
    data object Ended : GoalLockDetailSideEffect
}

private val GoalLockMode.detailLabel: String
    get() = when (this) {
        GoalLockMode.AllDay -> "하루종일 잠금"
        is GoalLockMode.Scheduled -> "특정 시간 잠금"
    }

private val GoalLockMode.analyticsLockMode: String
    get() = when (this) {
        GoalLockMode.AllDay -> AnalyticsGoalLockMode.ALL_DAY
        is GoalLockMode.Scheduled -> AnalyticsGoalLockMode.SCHEDULED
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

private fun durationDaysBucket(
    startDate: LocalDate,
    endDate: LocalDate,
): String = when (ChronoUnit.DAYS.between(startDate, endDate).plus(1).coerceAtLeast(1)) {
    in 1L..6L -> AnalyticsGoalLockDurationDaysBucket.ONE_TO_SIX
    7L -> AnalyticsGoalLockDurationDaysBucket.SEVEN
    in 8L..14L -> AnalyticsGoalLockDurationDaysBucket.EIGHT_TO_FOURTEEN
    in 15L..30L -> AnalyticsGoalLockDurationDaysBucket.FIFTEEN_TO_THIRTY
    else -> AnalyticsGoalLockDurationDaysBucket.THIRTY_ONE_PLUS
}
