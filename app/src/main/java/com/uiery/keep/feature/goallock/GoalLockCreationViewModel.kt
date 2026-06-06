package com.uiery.keep.feature.goallock

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockDurationSelectionType
import com.uiery.keep.analytics.AnalyticsGoalLockNameType
import com.uiery.keep.analytics.AnalyticsSelectedAppCountBucket
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.BlockingStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
internal class GoalLockCreationViewModel
    @Inject
    constructor(
        private val goalLockRepository: GoalLockRepository,
        private val analytics: KeepAnalytics,
        private val blockingStateStore: BlockingStateStore,
    ) : ViewModel(),
        ContainerHost<GoalLockCreationUiState, GoalLockCreationSideEffect> {
        override val container: Container<GoalLockCreationUiState, GoalLockCreationSideEffect> =
            container(GoalLockCreationUiState())

        internal fun setGoalName(goalName: String) =
            intent {
                reduce {
                    state.copy(
                        goalName = goalName,
                        goalNameType = goalName.analyticsGoalNameType,
                    )
                }
                updateCreateEnabled()
            }

        internal fun setDateRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ) = intent {
            reduce {
                state.copy(
                    startDate = startDate,
                    endDate = endDate,
                    durationSelectionType = AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
                )
            }
            updateCreateEnabled()
        }

        internal fun setCustomDurationDays(
            today: LocalDate,
            days: Int,
        ) = intent {
            val normalizedDays = days.coerceAtLeast(1)
            reduce {
                state.copy(
                    startDate = today,
                    endDate = today.plusDays((normalizedDays - 1).toLong()),
                    durationSelectionType = AnalyticsGoalLockDurationSelectionType.CUSTOM_DAYS,
                )
            }
            updateCreateEnabled()
        }

        internal fun setEndDateSelection(
            today: LocalDate,
            endDate: LocalDate,
        ) = intent {
            reduce {
                state.copy(
                    startDate = today,
                    endDate = endDate,
                    durationSelectionType = AnalyticsGoalLockDurationSelectionType.END_DATE,
                )
            }
            updateCreateEnabled()
        }

        internal fun setAllDayMode() =
            intent {
                reduce { state.copy(lockMode = GoalLockCreationLockMode.AllDay) }
                updateCreateEnabled()
            }

        internal fun setScheduledMode(
            repeatDays: Set<DayOfWeek>,
            startTime: LocalTime,
            endTime: LocalTime,
        ) = intent {
            reduce {
                state.copy(
                    lockMode = GoalLockCreationLockMode.Scheduled(
                        repeatDays = repeatDays,
                        startTime = startTime,
                        endTime = endTime,
                    ),
                )
            }
            updateCreateEnabled()
        }

        internal fun setSelectedApps(selectedApps: Set<String>) =
            intent {
                reduce { state.copy(selectedApps = selectedApps.normalizedPackages()) }
                updateCreateEnabled()
            }

        internal fun removeSelectedApp(packageName: String) =
            intent {
                reduce { state.copy(selectedApps = state.selectedApps - packageName.trim()) }
                updateCreateEnabled()
            }

        internal fun loadSelectedAppsFromCurrentSelection() =
            intent {
                val selectedApps = blockingStateStore.readSelectedAppPackages()
                reduce { state.copy(selectedApps = selectedApps) }
                updateCreateEnabled()
            }

        internal fun createGoalLock(
            durationSelectionType: String? = null,
            goalNameType: String? = null,
        ) = intent {
            val goalLock = state.toGoalLock()
            if (!state.isValidForCreation(goalLock)) return@intent

            val insertedId = goalLockRepository.create(goalLock)
            analytics.trackGoalLockCreated(
                durationSelectionType = durationSelectionType ?: state.durationSelectionType,
                lockMode = goalLock.lockMode.analyticsLockMode,
                selectedAppCountBucket = selectedAppCountBucket(goalLock.selectedPackages.size),
                goalNameType = goalNameType ?: state.goalNameType,
            )
            postSideEffect(GoalLockCreationSideEffect.Created(insertedId))
        }

        private fun updateCreateEnabled() =
            intent {
                reduce { state.copy(isCreateEnabled = state.isValidForCreation(state.toGoalLock())) }
            }
    }

data class GoalLockCreationUiState(
    val goalName: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val lockMode: GoalLockCreationLockMode = GoalLockCreationLockMode.AllDay,
    val selectedApps: Set<String> = emptySet(),
    val durationSelectionType: String = AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
    val goalNameType: String = AnalyticsGoalLockNameType.CUSTOM,
    val isCreateEnabled: Boolean = false,
)

sealed interface GoalLockCreationLockMode {
    data object AllDay : GoalLockCreationLockMode

    data class Scheduled(
        val repeatDays: Set<DayOfWeek>,
        val startTime: LocalTime,
        val endTime: LocalTime,
    ) : GoalLockCreationLockMode
}

sealed interface GoalLockCreationSideEffect {
    data class Created(val goalLockId: Long) : GoalLockCreationSideEffect
}

private fun GoalLockCreationUiState.toGoalLock() =
    GoalLock(
        id = 0L,
        goalName = goalName.trim(),
        startDate = startDate,
        endDate = endDate,
        lockMode = lockMode.toDomain(),
        selectedPackages = selectedApps,
        status = GoalLockStoredStatus.Active,
    )

private fun GoalLockCreationUiState.isValidForCreation(goalLock: GoalLock): Boolean =
    goalName.isNotBlank() &&
        GoalLockPolicy.isValidForCreation(goalLock) &&
        when (lockMode) {
            GoalLockCreationLockMode.AllDay -> true
            is GoalLockCreationLockMode.Scheduled -> lockMode.repeatDays.isNotEmpty()
        }

private fun GoalLockCreationLockMode.toDomain(): GoalLockMode =
    when (this) {
        GoalLockCreationLockMode.AllDay -> GoalLockMode.AllDay
        is GoalLockCreationLockMode.Scheduled -> GoalLockMode.Scheduled(
            repeatDays = repeatDays,
            startTime = startTime,
            endTime = endTime,
        )
    }

private fun selectedAppCountBucket(selectedAppCount: Int): String =
    when (selectedAppCount) {
        1 -> AnalyticsSelectedAppCountBucket.ONE
        in 2..3 -> AnalyticsSelectedAppCountBucket.TWO_TO_THREE
        in 4..6 -> AnalyticsSelectedAppCountBucket.FOUR_TO_SIX
        else -> AnalyticsSelectedAppCountBucket.SEVEN_PLUS
    }

private val String.analyticsGoalNameType: String
    get() = when (trim()) {
        "시험 준비" -> AnalyticsGoalLockNameType.PRESET_EXAM
        "SNS 줄이기" -> AnalyticsGoalLockNameType.PRESET_SNS
        "게임 줄이기" -> AnalyticsGoalLockNameType.PRESET_GAME
        "수면 습관" -> AnalyticsGoalLockNameType.PRESET_SLEEP
        else -> AnalyticsGoalLockNameType.CUSTOM
    }

private fun Set<String>.normalizedPackages(): Set<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
