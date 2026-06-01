package com.uiery.keep.feature.routine

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.routineDurationMinutes
import com.uiery.keep.util.timeNow
import com.uiery.keep.util.toDayOfWeekList
import com.uiery.keep.util.toRepeatDaysBinary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class RoutineBottomSheetViewModel
    @Inject
    constructor(
        private val routineDao: RoutineDao,
        private val routineScheduler: RoutineScheduler,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> {
        override val container: Container<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> =
            container(
                RoutineBottomSheetUiState(),
            )
        internal fun resetState() =
            intent {
                reduce { RoutineBottomSheetUiState() }
            }

        internal fun resetEditState(routineModel: RoutineModel) =
            intent {
                reduce {
                    val editState =
                        state.copy(
                            name = routineModel.name,
                            startTime = routineModel.startTime,
                            endTime = routineModel.endTime,
                            selectDays = routineModel.repeatDays.toDayOfWeekList(),
                            selectApps = routineModel.lockApplications?.toSet() ?: emptySet(),
                            isEnabled = routineModel.isEnabled,
                            changeLockHours = routineModel.changeLockHours,
                        )

                    editState.copy(isButtonEnable = editState.isValidForSave())
                }
            }

        internal fun setChangeLockHours(hours: Int?) =
            intent {
                reduce { state.copy(changeLockHours = hours) }
            }

        internal fun setName(name: String) =
            intent {
                reduce { state.copy(name = name) }
                setButtonEnabled()
            }

        internal fun setStartTime(startTime: LocalTime) =
            intent {
                reduce { state.copy(startTime = startTime) }
                setButtonEnabled()
            }

        internal fun setEndTime(endTime: LocalTime) =
            intent {
                reduce { state.copy(endTime = endTime) }
                setButtonEnabled()
            }

        internal fun setSelectDays(dayOfWeek: DayOfWeek) =
            intent {
                val selectDays = state.selectDays
                val updatedDays =
                    if (selectDays.contains(dayOfWeek)) {
                        selectDays.minus(dayOfWeek)
                    } else {
                        selectDays.plus(dayOfWeek)
                    }
                reduce { state.copy(selectDays = updatedDays) }
                setButtonEnabled()
            }

        internal fun setSelectApps(selectApps: Set<String>) =
            intent {
                reduce { state.copy(selectApps = selectApps) }
                setButtonEnabled()
            }

        private fun setButtonEnabled() =
            intent {
                reduce { state.copy(isButtonEnable = state.isValidForSave()) }
            }

        internal fun addRoutine() =
            intent {
                val routineModel = state.toRoutineModel()
                val resolvedRoutine = resolveRoutineExactAlarmPermission(
                    routine = routineModel,
                    canScheduleExactAlarms = routineScheduler.canScheduleExactAlarms(),
                )
                val insertedId = routineDao.insert(routineEntity = resolvedRoutine.routine.toEntity())
                val routineWithId = resolvedRoutine.routine.copy(id = insertedId)
                var shouldShowPermissionPrompt = resolvedRoutine.shouldShowPermissionPrompt
                if (routineWithId.isEnabled) {
                    when (routineScheduler.scheduleRoutine(routineWithId)) {
                        com.uiery.keep.notification.RoutineScheduleResult.Scheduled -> {
                            analytics.trackLockScheduled(
                                scheduleType = AnalyticsScheduleType.ROUTINE,
                                scheduledDurationMinutes = routineDurationMinutes(routineWithId.startTime, routineWithId.endTime),
                            )
                        }
                        com.uiery.keep.notification.RoutineScheduleResult.MissingExactAlarmPermission -> {
                            routineDao.update(routineWithId.copy(isEnabled = false).toEntity())
                            shouldShowPermissionPrompt = true
                        }
                        com.uiery.keep.notification.RoutineScheduleResult.NotEnabled -> Unit
                    }
                }
                if (shouldShowPermissionPrompt) {
                    postSideEffect(RoutineBottomSheetSideEffect.ShowAlarmPermission)
                }
            }

        internal fun editRoutine(id: Long?) =
            intent {
                id?.let {
                    runCatching {
                        val routineModel = state.toRoutineModel(id = it)
                        val resolvedRoutine = resolveRoutineExactAlarmPermission(
                            routine = routineModel,
                            canScheduleExactAlarms = routineScheduler.canScheduleExactAlarms(),
                        )
                        routineDao.update(resolvedRoutine.routine.toEntity())
                        routineScheduler.cancelRoutine(id)
                        var shouldShowPermissionPrompt = resolvedRoutine.shouldShowPermissionPrompt
                        if (resolvedRoutine.routine.isEnabled) {
                            when (routineScheduler.scheduleRoutine(resolvedRoutine.routine)) {
                                com.uiery.keep.notification.RoutineScheduleResult.Scheduled -> {
                                    analytics.trackLockScheduled(
                                        scheduleType = AnalyticsScheduleType.ROUTINE,
                                        scheduledDurationMinutes = routineDurationMinutes(
                                            resolvedRoutine.routine.startTime,
                                            resolvedRoutine.routine.endTime,
                                        ),
                                    )
                                }
                                com.uiery.keep.notification.RoutineScheduleResult.MissingExactAlarmPermission -> {
                                    routineDao.update(resolvedRoutine.routine.copy(isEnabled = false).toEntity())
                                    shouldShowPermissionPrompt = true
                                }
                                com.uiery.keep.notification.RoutineScheduleResult.NotEnabled -> Unit
                            }
                        }
                        if (shouldShowPermissionPrompt) {
                            postSideEffect(RoutineBottomSheetSideEffect.ShowAlarmPermission)
                        }
                    }
                }
            }
    }

data class RoutineBottomSheetUiState(
    val name: String = "",
    val startTime: LocalTime = timeNow,
    val endTime: LocalTime = timeNow.toJavaLocalTime().plusHours(1).toKotlinLocalTime(),
    val selectDays: List<DayOfWeek> = emptyList(),
    val isButtonEnable: Boolean = false,
    val selectApps: Set<String> = emptySet(),
    val isEnabled: Boolean = true,
    val changeLockHours: Int? = null,
)

private fun RoutineBottomSheetUiState.isValidForSave(): Boolean {
    val isNameValid = name.isNotEmpty()
    val isTimeValid = routineDurationMinutes(startTime, endTime) >= 15
    val isDaySelected = selectDays.isNotEmpty()
    return isNameValid && isTimeValid && isDaySelected && selectApps.isNotEmpty()
}

private fun RoutineBottomSheetUiState.toRoutineModel(id: Long = 0) =
    RoutineModel(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        repeatDays = selectDays.toRepeatDaysBinary(),
        lockApplications = selectApps.toList(),
        isEnabled = isEnabled,
        changeLockHours = changeLockHours,
    )

sealed interface RoutineBottomSheetSideEffect {
    data object ShowAlarmPermission : RoutineBottomSheetSideEffect
}
