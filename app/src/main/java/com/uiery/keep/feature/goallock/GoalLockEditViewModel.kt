package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockChangedField
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockPolicy
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
internal class GoalLockEditViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val goalLockRepository: GoalLockRepository,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<GoalLockEditUiState, GoalLockEditSideEffect> {
        private val goalLockId: Long = checkNotNull(savedStateHandle[GOAL_LOCK_ID_ARG])
        private var lastLoadDate: LocalDate = LocalDate.now()

        override val container: Container<GoalLockEditUiState, GoalLockEditSideEffect> =
            container(GoalLockEditUiState())

        init {
            analytics.logScreenView(KeepAnalyticsScreen.GOAL_LOCK_EDIT)
        }

        fun loadGoalLock(today: LocalDate = LocalDate.now()) =
            intent {
                lastLoadDate = today
                reduce { state.copy(isLoading = true, error = null) }
                try {
                    val goalLock = goalLockRepository.fetch(goalLockId)
                    when {
                        goalLock == null -> {
                            reduce { state.copy(isLoading = false) }
                            postSideEffect(GoalLockEditSideEffect.NotFound)
                        }
                        goalLock.status != GoalLockStoredStatus.Active -> {
                            reduce { state.copy(isLoading = false) }
                            postSideEffect(GoalLockEditSideEffect.Unavailable)
                        }
                        GoalLockPolicy.runtimeStatus(goalLock, today.atStartOfDay()) ==
                            GoalLockRuntimeStatus.Completed -> {
                            completeExpiredGoal(goalLock)
                            reduce { state.copy(isLoading = false) }
                            postSideEffect(GoalLockEditSideEffect.Unavailable)
                        }
                        else -> reduce {
                            GoalLockEditUiState.from(goalLock = goalLock, today = today)
                        }
                    }
                } catch (_: Exception) {
                    reduce {
                        state.copy(
                            isLoading = false,
                            error = GoalLockEditError.Load,
                        )
                    }
                }
            }

        fun retryLoad() = loadGoalLock(lastLoadDate)

        fun setGoalName(goalName: String) =
            intent {
                reduce { state.copy(goalName = goalName, error = state.error.withoutSave()) }
            }

        fun setDurationDays(days: Int) =
            intent {
                val startDate = state.startDate ?: return@intent
                val normalizedDays = days.coerceAtLeast(1)
                reduce {
                    state.copy(
                        endDate = startDate.plusDays((normalizedDays - 1).toLong()),
                        error = state.error.withoutSave(),
                    )
                }
            }

        fun setEndDate(endDate: LocalDate) =
            intent {
                reduce { state.copy(endDate = endDate, error = state.error.withoutSave()) }
            }

        fun setAllDayMode() =
            intent {
                reduce { state.copy(lockMode = GoalLockMode.AllDay, error = state.error.withoutSave()) }
            }

        fun setWeekdayEveningMode() =
            intent {
                if (state.lockMode is GoalLockMode.Scheduled) return@intent
                reduce {
                    state.copy(
                        lockMode = weekdayEveningMode(),
                        error = state.error.withoutSave(),
                    )
                }
            }

        fun setSelectedApps(selectedApps: Set<String>) =
            intent {
                reduce {
                    state.copy(
                        selectedPackages = selectedApps.normalizedPackages(),
                        error = state.error.withoutSave(),
                    )
                }
            }

        fun removeSelectedApp(packageName: String) =
            intent {
                reduce {
                    state.copy(
                        selectedPackages = state.selectedPackages - packageName.trim(),
                        error = state.error.withoutSave(),
                    )
                }
            }

        fun requestBack() =
            intent {
                if (state.isDirty) {
                    reduce { state.copy(showDiscardConfirmation = true) }
                } else {
                    postSideEffect(GoalLockEditSideEffect.NavigateBack)
                }
            }

        fun cancelDiscard() =
            intent {
                reduce { state.copy(showDiscardConfirmation = false) }
            }

        fun confirmDiscard() =
            intent {
                reduce { state.copy(showDiscardConfirmation = false) }
                postSideEffect(GoalLockEditSideEffect.NavigateBack)
            }

        fun save(today: LocalDate = LocalDate.now()) =
            intent {
                if (!state.canSave) return@intent
                val draft = state
                reduce { state.copy(isSaving = true, error = null) }

                try {
                    val latest = goalLockRepository.fetch(goalLockId)
                    if (latest == null) {
                        reduce { state.copy(isSaving = false) }
                        postSideEffect(GoalLockEditSideEffect.NotFound)
                        return@intent
                    }
                    if (latest.status != GoalLockStoredStatus.Active) {
                        reduce { state.copy(isSaving = false) }
                        postSideEffect(GoalLockEditSideEffect.Unavailable)
                        return@intent
                    }
                    if (GoalLockPolicy.runtimeStatus(latest, today.atStartOfDay()) ==
                        GoalLockRuntimeStatus.Completed
                    ) {
                        completeExpiredGoal(latest)
                        reduce { state.copy(isSaving = false) }
                        postSideEffect(GoalLockEditSideEffect.Unavailable)
                        return@intent
                    }

                    val updated = latest.copy(
                        goalName = draft.goalName.trim(),
                        endDate = checkNotNull(draft.endDate),
                        lockMode = checkNotNull(draft.lockMode),
                        selectedPackages = draft.selectedPackages.normalizedPackages(),
                    )
                    if (!updated.isValidEdit(today)) {
                        reduce { state.copy(isSaving = false) }
                        return@intent
                    }

                    goalLockRepository.update(updated)
                    trackChanges(previous = latest, updated = updated)
                    reduce {
                        GoalLockEditUiState.from(goalLock = updated, today = today)
                    }
                    postSideEffect(GoalLockEditSideEffect.Saved)
                } catch (_: Exception) {
                    reduce {
                        state.copy(
                            isSaving = false,
                            error = GoalLockEditError.Save,
                        )
                    }
                }
            }

        private fun completeExpiredGoal(goalLock: GoalLock) {
            val completed = goalLock.copy(status = GoalLockStoredStatus.Completed)
            goalLockRepository.update(completed)
            analytics.trackGoalLockCompleted(
                lockMode = goalLock.lockMode.analyticsLockMode,
                durationDaysBucket = goalLockDurationDaysBucket(goalLock.startDate, goalLock.endDate),
            )
        }

        private fun trackChanges(
            previous: GoalLock,
            updated: GoalLock,
        ) {
            val changedFields = buildList {
                if (previous.goalName != updated.goalName) add(AnalyticsGoalLockChangedField.NAME)
                if (previous.endDate != updated.endDate) add(AnalyticsGoalLockChangedField.DURATION)
                if (previous.lockMode != updated.lockMode) {
                    add(
                        if (previous.lockMode::class == updated.lockMode::class) {
                            AnalyticsGoalLockChangedField.SCHEDULE
                        } else {
                            AnalyticsGoalLockChangedField.LOCK_MODE
                        },
                    )
                }
                if (previous.selectedPackages != updated.selectedPackages) {
                    add(AnalyticsGoalLockChangedField.APPS)
                }
            }
            changedFields.forEach { changedField ->
                analytics.trackGoalLockUpdated(
                    lockMode = updated.lockMode.analyticsLockMode,
                    changedField = changedField,
                )
            }
        }
    }

internal data class GoalLockEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val originalGoal: GoalLock? = null,
    val goalName: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val lockMode: GoalLockMode? = null,
    val selectedPackages: Set<String> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val showDiscardConfirmation: Boolean = false,
    val error: GoalLockEditError? = null,
) {
    val isDirty: Boolean
        get() {
            val original = originalGoal ?: return false
            return goalName.trim() != original.goalName ||
                endDate != original.endDate ||
                lockMode != original.lockMode ||
                selectedPackages.normalizedPackages() != original.selectedPackages.normalizedPackages()
        }

    val isValid: Boolean
        get() {
            val original = originalGoal ?: return false
            val start = startDate ?: return false
            val end = endDate ?: return false
            val mode = lockMode ?: return false
            val minimumEndDate = when (
                GoalLockPolicy.runtimeStatus(original, today.atStartOfDay())
            ) {
                GoalLockRuntimeStatus.Pending -> start
                GoalLockRuntimeStatus.Active -> today
                GoalLockRuntimeStatus.Completed,
                GoalLockRuntimeStatus.EndedEarly,
                -> return false
            }
            return goalName.isNotBlank() &&
                selectedPackages.normalizedPackages().isNotEmpty() &&
                !end.isBefore(start) &&
                !end.isBefore(minimumEndDate) &&
                mode.isValidEditMode()
        }

    val canSave: Boolean
        get() = !isLoading && !isSaving && isDirty && isValid

    val totalDurationDays: Int
        get() {
            val start = startDate ?: return 1
            val end = endDate ?: return 1
            return (ChronoUnit.DAYS.between(start, end).coerceAtLeast(0) + 1).toInt()
        }

    companion object {
        fun from(
            goalLock: GoalLock,
            today: LocalDate,
        ) = GoalLockEditUiState(
            isLoading = false,
            originalGoal = goalLock,
            goalName = goalLock.goalName,
            startDate = goalLock.startDate,
            endDate = goalLock.endDate,
            lockMode = goalLock.lockMode,
            selectedPackages = goalLock.selectedPackages.normalizedPackages(),
            today = today,
        )
    }
}

internal enum class GoalLockEditError {
    Load,
    Save,
}

internal sealed interface GoalLockEditSideEffect {
    data object Saved : GoalLockEditSideEffect
    data object NavigateBack : GoalLockEditSideEffect
    data object NotFound : GoalLockEditSideEffect
    data object Unavailable : GoalLockEditSideEffect
}

private fun GoalLockEditError?.withoutSave(): GoalLockEditError? =
    takeUnless { it == GoalLockEditError.Save }

internal fun weekdayEveningMode(): GoalLockMode.Scheduled = GoalLockMode.Scheduled(
    repeatDays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    startTime = LocalTime.of(19, 0),
    endTime = LocalTime.of(23, 0),
)

private fun GoalLockMode.isValidEditMode(): Boolean = when (this) {
    GoalLockMode.AllDay -> true
    is GoalLockMode.Scheduled -> repeatDays.isNotEmpty() && startTime != endTime
}

private fun GoalLock.isValidEdit(today: LocalDate): Boolean {
    val minimumEndDate = when (GoalLockPolicy.runtimeStatus(this, today.atStartOfDay())) {
        GoalLockRuntimeStatus.Pending -> startDate
        GoalLockRuntimeStatus.Active -> today
        GoalLockRuntimeStatus.Completed,
        GoalLockRuntimeStatus.EndedEarly,
        -> return false
    }
    return goalName.isNotBlank() &&
        selectedPackages.normalizedPackages().isNotEmpty() &&
        !endDate.isBefore(startDate) &&
        !endDate.isBefore(minimumEndDate) &&
        lockMode.isValidEditMode()
}

private fun Set<String>.normalizedPackages(): Set<String> =
    map(String::trim)
        .filter(String::isNotBlank)
        .toSet()

