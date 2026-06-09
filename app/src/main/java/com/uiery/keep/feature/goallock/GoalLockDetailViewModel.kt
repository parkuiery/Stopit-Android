package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockChangedField
import com.uiery.keep.analytics.AnalyticsGoalLockElapsedDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockEndedEarlyReason
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
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
        private val goalLockRepository: GoalLockRepository,
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
                val goalLock = goalLockRepository.fetch(goalLockId)
                if (goalLock == null) {
                    postSideEffect(GoalLockDetailSideEffect.NotFound)
                    return@intent
                }

                val normalizedGoalLock = completeIfExpired(goalLock = goalLock, today = today)
                reduce {
                    state.copy(
                        goalLock = normalizedGoalLock,
                        showEndConfirmation = false,
                        pendingSelectedApps = emptySet(),
                        showUpdateAppsConfirmation = false,
                        pendingGoalName = normalizedGoalLock.goalName,
                        showUpdateGoalNameConfirmation = false,
                        pendingDurationDays = normalizedGoalLock.durationDays,
                        pendingLockMode = normalizedGoalLock.lockMode,
                        showUpdateDurationConfirmation = false,
                        showUpdateLockModeConfirmation = false,
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

        fun requestUpdateSelectedApps(selectedApps: Set<String>) =
            intent {
                val current = state.goalLock ?: return@intent
                if (current.status != GoalLockStoredStatus.Active || state.isEnded || state.isCompleted) return@intent
                val normalizedSelectedApps = selectedApps.normalizedPackages()
                reduce {
                    state.copy(
                        pendingSelectedApps = normalizedSelectedApps,
                        showUpdateAppsConfirmation = normalizedSelectedApps.isNotEmpty(),
                    )
                }
            }

        fun cancelUpdateSelectedApps() =
            intent {
                reduce {
                    state.copy(
                        pendingSelectedApps = emptySet(),
                        showUpdateAppsConfirmation = false,
                    )
                }
            }

        fun confirmUpdateSelectedApps() =
            intent {
                val current = state.goalLock ?: return@intent
                val selectedApps = state.pendingSelectedApps
                if (current.status != GoalLockStoredStatus.Active || selectedApps.isEmpty()) {
                    reduce {
                        state.copy(
                            pendingSelectedApps = emptySet(),
                            showUpdateAppsConfirmation = false,
                        )
                    }
                    return@intent
                }

                val updated = current.copy(selectedPackages = selectedApps)
                goalLockRepository.update(updated)
                analytics.trackGoalLockUpdated(
                    lockMode = current.lockMode.analyticsLockMode,
                    changedField = AnalyticsGoalLockChangedField.APPS,
                )
                reduce {
                    state.copy(
                        goalLock = updated,
                        pendingSelectedApps = emptySet(),
                        showUpdateAppsConfirmation = false,
                    )
                }
            }

        fun requestUpdateGoalName(goalName: String) =
            intent {
                val current = state.goalLock ?: return@intent
                if (current.status != GoalLockStoredStatus.Active || state.isEnded || state.isCompleted) return@intent
                val normalizedGoalName = goalName.trim()
                reduce {
                    state.copy(
                        pendingGoalName = goalName,
                        showUpdateGoalNameConfirmation = normalizedGoalName.isNotBlank() && normalizedGoalName != current.goalName,
                    )
                }
            }

        fun cancelUpdateGoalName() =
            intent {
                reduce {
                    state.copy(
                        pendingGoalName = state.goalLock?.goalName.orEmpty(),
                        showUpdateGoalNameConfirmation = false,
                    )
                }
            }

        fun confirmUpdateGoalName() =
            intent {
                val current = state.goalLock ?: return@intent
                val goalName = state.pendingGoalName.trim()
                if (current.status != GoalLockStoredStatus.Active || goalName.isBlank() || goalName == current.goalName) {
                    reduce {
                        state.copy(
                            pendingGoalName = current.goalName,
                            showUpdateGoalNameConfirmation = false,
                        )
                    }
                    return@intent
                }

                val updated = current.copy(goalName = goalName)
                goalLockRepository.update(updated)
                analytics.trackGoalLockUpdated(
                    lockMode = current.lockMode.analyticsLockMode,
                    changedField = AnalyticsGoalLockChangedField.NAME,
                )
                reduce {
                    state.copy(
                        goalLock = updated,
                        pendingGoalName = updated.goalName,
                        showUpdateGoalNameConfirmation = false,
                    )
                }
            }

        fun requestUpdateDurationDays(days: Int) =
            intent {
                val current = state.goalLock ?: return@intent
                if (current.status != GoalLockStoredStatus.Active || state.isEnded || state.isCompleted) return@intent
                val normalizedDays = days.coerceAtLeast(1)
                reduce {
                    state.copy(
                        pendingDurationDays = normalizedDays,
                        showUpdateDurationConfirmation = normalizedDays != current.durationDays,
                    )
                }
            }

        fun cancelUpdateDuration() =
            intent {
                reduce {
                    state.copy(
                        pendingDurationDays = state.goalLock?.durationDays ?: state.pendingDurationDays,
                        showUpdateDurationConfirmation = false,
                    )
                }
            }

        fun confirmUpdateDuration(today: LocalDate = LocalDate.now()) =
            intent {
                val current = state.goalLock ?: return@intent
                val durationDays = state.pendingDurationDays.coerceAtLeast(1)
                val updatedEndDate = current.startDate.plusDays((durationDays - 1).toLong())
                if (current.status != GoalLockStoredStatus.Active || updatedEndDate == current.endDate) {
                    reduce {
                        state.copy(
                            pendingDurationDays = current.durationDays,
                            showUpdateDurationConfirmation = false,
                        )
                    }
                    return@intent
                }

                val updated = current.copy(endDate = updatedEndDate)
                analytics.trackGoalLockUpdated(
                    lockMode = current.lockMode.analyticsLockMode,
                    changedField = AnalyticsGoalLockChangedField.DURATION,
                )
                val normalizedUpdated = completeIfExpired(goalLock = updated, today = today)
                if (normalizedUpdated.status == GoalLockStoredStatus.Active) {
                    goalLockRepository.update(updated)
                }
                reduce {
                    state.copy(
                        goalLock = normalizedUpdated,
                        pendingDurationDays = normalizedUpdated.durationDays,
                        showUpdateDurationConfirmation = false,
                        isEnded = normalizedUpdated.status == GoalLockStoredStatus.EndedEarly,
                        isCompleted = normalizedUpdated.status == GoalLockStoredStatus.Completed,
                    )
                }
            }

        fun requestUpdateLockMode(lockMode: GoalLockMode) =
            intent {
                val current = state.goalLock ?: return@intent
                if (current.status != GoalLockStoredStatus.Active || state.isEnded || state.isCompleted) return@intent
                if (!lockMode.isValidForUpdate()) return@intent
                reduce {
                    state.copy(
                        pendingLockMode = lockMode,
                        showUpdateLockModeConfirmation = lockMode != current.lockMode,
                    )
                }
            }

        fun cancelUpdateLockMode() =
            intent {
                reduce {
                    state.copy(
                        pendingLockMode = state.goalLock?.lockMode ?: state.pendingLockMode,
                        showUpdateLockModeConfirmation = false,
                    )
                }
            }

        fun confirmUpdateLockMode() =
            intent {
                val current = state.goalLock ?: return@intent
                val lockMode = state.pendingLockMode
                if (current.status != GoalLockStoredStatus.Active || lockMode == null || lockMode == current.lockMode) {
                    reduce {
                        state.copy(
                            pendingLockMode = current.lockMode,
                            showUpdateLockModeConfirmation = false,
                        )
                    }
                    return@intent
                }
                if (!lockMode.isValidForUpdate()) {
                    reduce {
                        state.copy(
                            pendingLockMode = current.lockMode,
                            showUpdateLockModeConfirmation = false,
                        )
                    }
                    return@intent
                }

                val updated = current.copy(lockMode = lockMode)
                goalLockRepository.update(updated)
                analytics.trackGoalLockUpdated(
                    lockMode = updated.lockMode.analyticsLockMode,
                    changedField = current.lockMode.changedFieldFor(updated.lockMode),
                )
                reduce {
                    state.copy(
                        goalLock = updated,
                        pendingLockMode = updated.lockMode,
                        showUpdateLockModeConfirmation = false,
                    )
                }
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
                goalLockRepository.update(ended)
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
            goalLockRepository.update(completed)
            analytics.trackGoalLockCompleted(
                lockMode = goalLock.lockMode.analyticsLockMode,
                durationDaysBucket = goalLockDurationDaysBucket(goalLock.startDate, goalLock.endDate),
            )
            return completed
        }
    }

internal data class GoalLockDetailUiState(
    val goalLock: GoalLock? = null,
    val showEndConfirmation: Boolean = false,
    val pendingSelectedApps: Set<String> = emptySet(),
    val showUpdateAppsConfirmation: Boolean = false,
    val pendingGoalName: String = "",
    val showUpdateGoalNameConfirmation: Boolean = false,
    val pendingDurationDays: Int = 1,
    val showUpdateDurationConfirmation: Boolean = false,
    val pendingLockMode: GoalLockMode? = null,
    val showUpdateLockModeConfirmation: Boolean = false,
    val isEnded: Boolean = false,
    val isCompleted: Boolean = false,
) {
    val goalName: String = goalLock?.goalName.orEmpty()
    val selectedAppCount: Int = goalLock?.selectedPackages?.size ?: 0
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

private val GoalLock.durationDays: Int
    get() = (ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0) + 1).toInt()

private fun GoalLockMode.isValidForUpdate(): Boolean = when (this) {
    GoalLockMode.AllDay -> true
    is GoalLockMode.Scheduled -> repeatDays.isNotEmpty() && startTime != endTime
}

private fun GoalLockMode.changedFieldFor(updated: GoalLockMode): String =
    if (this::class == updated::class) AnalyticsGoalLockChangedField.SCHEDULE else AnalyticsGoalLockChangedField.LOCK_MODE

private fun Set<String>.normalizedPackages(): Set<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
