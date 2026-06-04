package com.uiery.keep.feature.goallock

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.AnalyticsSelectedAppCountBucket
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
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
class GoalLockCreationViewModel
    @Inject
    constructor(
        private val goalLockDao: GoalLockDao,
        private val analytics: KeepAnalytics,
        private val blockingStateStore: BlockingStateStore,
    ) : ViewModel(),
        ContainerHost<GoalLockCreationUiState, GoalLockCreationSideEffect> {
        override val container: Container<GoalLockCreationUiState, GoalLockCreationSideEffect> =
            container(GoalLockCreationUiState())

        internal fun setGoalName(goalName: String) =
            intent {
                reduce { state.copy(goalName = goalName) }
                updateCreateEnabled()
            }

        internal fun setDateRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ) = intent {
            reduce { state.copy(startDate = startDate, endDate = endDate) }
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
                reduce { state.copy(selectedApps = selectedApps) }
                updateCreateEnabled()
            }

        internal fun loadSelectedAppsFromCurrentSelection() =
            intent {
                val selectedApps = blockingStateStore.readSelectedAppPackages()
                reduce { state.copy(selectedApps = selectedApps) }
                updateCreateEnabled()
            }

        internal fun createGoalLock(
            durationSelectionType: String,
            goalNameType: String,
        ) = intent {
            val goalLock = state.toGoalLock()
            if (!state.isValidForCreation(goalLock)) return@intent

            val insertedId = goalLockDao.insert(GoalLockEntity.fromDomain(goalLock))
            analytics.trackGoalLockCreated(
                durationSelectionType = durationSelectionType,
                lockMode = goalLock.lockMode.analyticsLockMode,
                selectedAppCountBucket = selectedAppCountBucket(goalLock.selectedPackages.size),
                goalNameType = goalNameType,
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

private val GoalLockMode.analyticsLockMode: String
    get() = when (this) {
        GoalLockMode.AllDay -> AnalyticsGoalLockMode.ALL_DAY
        is GoalLockMode.Scheduled -> AnalyticsGoalLockMode.SCHEDULED
    }

private fun selectedAppCountBucket(selectedAppCount: Int): String =
    when (selectedAppCount) {
        1 -> AnalyticsSelectedAppCountBucket.ONE
        in 2..3 -> AnalyticsSelectedAppCountBucket.TWO_TO_THREE
        in 4..6 -> AnalyticsSelectedAppCountBucket.FOUR_TO_SIX
        else -> AnalyticsSelectedAppCountBucket.SEVEN_PLUS
    }
