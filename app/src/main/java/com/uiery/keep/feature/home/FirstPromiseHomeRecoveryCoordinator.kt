package com.uiery.keep.feature.home

import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FirstPromiseResumeCardState(
    val routineId: Long,
    val appLabel: String,
    val startMinutes: Int,
    val repeatDays: Set<Int>,
    val isRetry: Boolean = false,
    val isBusy: Boolean = false,
)

sealed interface FirstPromiseResumeDecision {
    data class Show(val card: FirstPromiseResumeCardState) : FirstPromiseResumeDecision
    data object OpenSettings : FirstPromiseResumeDecision
    data object Hidden : FirstPromiseResumeDecision
}

interface FirstPromiseHomeRecovery {
    suspend fun load(): FirstPromiseResumeDecision
    suspend fun activate(): FirstPromiseResumeDecision
    suspend fun onResume(): FirstPromiseResumeDecision
    suspend fun onSettingsLaunchFailed(): FirstPromiseResumeDecision
}

object NoOpFirstPromiseHomeRecovery : FirstPromiseHomeRecovery {
    override suspend fun load() = FirstPromiseResumeDecision.Hidden
    override suspend fun activate() = FirstPromiseResumeDecision.Hidden
    override suspend fun onResume() = FirstPromiseResumeDecision.Hidden
    override suspend fun onSettingsLaunchFailed() = FirstPromiseResumeDecision.Hidden
}

@Singleton
class FirstPromiseHomeRecoveryCoordinator internal constructor(
    private val draftStore: FirstPromiseDraftStore,
    private val coordinator: FirstPromisePersistenceCoordinator,
    private val canScheduleExactAlarms: () -> Boolean,
) : FirstPromiseHomeRecovery {
    @Inject constructor(
        draftStore: FirstPromiseDraftStore,
        coordinator: FirstPromisePersistenceCoordinator,
        exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
    ) : this(draftStore, coordinator, exactAlarmOrchestrator::canScheduleExactAlarms)

    private val mutex = Mutex()
    internal var settingsLaunchPending: Boolean = false
        private set

    override suspend fun load(): FirstPromiseResumeDecision = mutex.withLock {
        resolve(mayOpenSettings = false)
    }

    override suspend fun activate(): FirstPromiseResumeDecision = mutex.withLock {
        if (settingsLaunchPending) return@withLock resolve(mayOpenSettings = false)
        resolve(mayOpenSettings = true)
    }

    override suspend fun onResume(): FirstPromiseResumeDecision = mutex.withLock {
        if (!settingsLaunchPending) return@withLock resolve(mayOpenSettings = false)
        settingsLaunchPending = false
        resolve(mayOpenSettings = false)
    }

    override suspend fun onSettingsLaunchFailed(): FirstPromiseResumeDecision = mutex.withLock {
        settingsLaunchPending = false
        resolve(mayOpenSettings = false)
    }

    private suspend fun resolve(mayOpenSettings: Boolean): FirstPromiseResumeDecision {
        val state = draftStore.readState()
        if (
            state.phase != FirstPromisePhase.CompletedDisabled ||
            state.scheduleState != FirstPromiseScheduleState.DisabledExactAlarmMissing
        ) return FirstPromiseResumeDecision.Hidden
        val routineId = state.routineId ?: return FirstPromiseResumeDecision.Hidden
        val mapping = coordinator.readCurrentMapping() ?: return FirstPromiseResumeDecision.Hidden
        if (mapping.routineId != routineId || mapping.routine.isEnabled) {
            return FirstPromiseResumeDecision.Hidden
        }
        if (!canScheduleExactAlarms()) {
            if (mayOpenSettings) {
                settingsLaunchPending = true
                return FirstPromiseResumeDecision.OpenSettings
            }
            return FirstPromiseResumeDecision.Show(mapping.toCard())
        }
        return when (val result = coordinator.finalizeExistingRoutine(routineId)) {
            is FirstPromisePersistenceResult.Succeeded ->
                if (
                    result.creation.scheduleState == FirstPromiseScheduleState.Enabled &&
                    result.creation.routine.isEnabled &&
                    result.creation.schedulingSucceeded
                ) {
                    FirstPromiseResumeDecision.Hidden
                } else {
                    FirstPromiseResumeDecision.Show(result.creation.toCard(isRetry = true))
                }
            else -> FirstPromiseResumeDecision.Show(mapping.toCard(isRetry = true))
        }
    }
}

private fun com.uiery.keep.data.firstpromise.FirstPromiseCreationResult.toCard(
    isRetry: Boolean = false,
): FirstPromiseResumeCardState = FirstPromiseResumeCardState(
    routineId = routineId,
    appLabel = routine.name,
    startMinutes = routine.startTime.hour * 60 + routine.startTime.minute,
    repeatDays = routine.repeatDays.mapIndexedNotNull { index, value ->
        (index + 1).takeIf { value == '1' }
    }.toSet(),
    isRetry = isRetry,
)
