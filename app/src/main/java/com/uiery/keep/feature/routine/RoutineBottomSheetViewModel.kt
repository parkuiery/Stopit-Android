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
                    state.copy(
                        name = routineModel.name,
                        startTime = routineModel.startTime,
                        endTime = routineModel.endTime,
                        selectDays = routineModel.repeatDays.toDayOfWeekList(),
                        selectApps = routineModel.lockApplications?.toSet() ?: emptySet(),
                        changeLockHours = routineModel.changeLockHours,
                    )
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
                val isNameValid = state.name.isNotEmpty()
                val isTimeValid = routineDurationMinutes(state.startTime, state.endTime) >= 15
                val isDaySelected = state.selectDays.isNotEmpty()
                val isEnabled = isNameValid && isTimeValid && isDaySelected && state.selectApps.isNotEmpty()
                reduce { state.copy(isButtonEnable = isEnabled) }
            }

        internal fun addRoutine() =
            intent {
                val routineModel = state.toRoutineModel()
                val insertedId = routineDao.insert(routineEntity = routineModel.toEntity())
                val routineWithId = routineModel.copy(id = insertedId)
                if (routineModel.isEnabled) {
                    analytics.trackLockScheduled(
                        scheduleType = AnalyticsScheduleType.ROUTINE,
                        scheduledDurationMinutes = routineDurationMinutes(routineModel.startTime, routineModel.endTime),
                    )
                    routineScheduler.scheduleRoutine(routineWithId)
                }
            }

        internal fun editRoutine(id: Long?) =
            intent {
                id?.let {
                    runCatching {
                        val routineModel = state.toRoutineModel(id = it)
                        routineDao.update(routineModel.toEntity())
                        routineScheduler.cancelRoutine(id)
                        if (state.isEnabled) {
                            analytics.trackLockScheduled(
                                scheduleType = AnalyticsScheduleType.ROUTINE,
                                scheduledDurationMinutes = routineDurationMinutes(
                                    routineModel.startTime,
                                    routineModel.endTime,
                                ),
                            )
                            routineScheduler.scheduleRoutine(routineModel)
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

sealed interface RoutineBottomSheetSideEffect
