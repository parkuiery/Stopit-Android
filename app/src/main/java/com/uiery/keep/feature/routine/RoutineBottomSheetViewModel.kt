package com.uiery.keep.feature.routine

import androidx.lifecycle.ViewModel
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.network.Retrofit
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
    ) : ViewModel(),
        ContainerHost<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> {
        override val container: Container<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> =
            container(
                RoutineBottomSheetUiState(),
            )

        val routineService = Retrofit.routineService

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
                    )
                }
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
                val routineModel =
                    RoutineModel(
                        id = 0,
                        name = state.name,
                        startTime = state.startTime,
                        endTime = state.endTime,
                        repeatDays = state.selectDays.toRepeatDaysBinary(),
                        lockApplications = state.selectApps.toList(),
                        isEnabled = state.isEnabled,
                    )
                val insertedId =
                    routineDao.insert(
                        routineEntity =
                            RoutineEntity(
                                name = state.name,
                                startTime = state.startTime,
                                endTime = state.endTime,
                                repeatDays = state.selectDays,
                                lockApplications = state.selectApps.toList(),
                                isEnabled = state.isEnabled,
                            ),
                    )
                val routineWithId = routineModel.copy(id = insertedId)
                if (routineModel.isEnabled) {
                    routineScheduler.scheduleRoutine(routineWithId)
                }
//        postSideEffect(
//            RoutineBottomSheetSideEffect.AddRoutineSuccess(
//                RoutineModel(
//                    id = it.id,
//                    localRoutineId = null,
//                    name = state.name,
//                    startTime = state.startTime.toString(),
//                    endTime = state.endTime.toString(),
//                    repeatDays = state.selectDays.toRepeatDaysBinary(),
//                    lockApplications = state.selectApps.toList(),
//                    isEnabled = true,
//                )
//            )
//        )
//        runCatching {
//            routineService.createRoutine(
//                createRoutineRequest = CreateRoutineRequest(
//                    localRoutineId = null,
//                    name = state.name,
//                    startTime = state.startTime.toString(),
//                    endTime = state.endTime.toString(),
//                    repeatDays = state.selectDays.toRepeatDaysBinary(),
//                    lockApplications = state.selectApps.toList(),
//                    deviceId = deviceId(),
//                )
//            )
//        }.onSuccess {
//
//        }
            }

        internal fun editRoutine(id: Long?) =
            intent {
                id?.let {
                    runCatching {
                        val routineModel =
                            RoutineModel(
                                id = it,
                                name = state.name,
                                startTime = state.startTime,
                                endTime = state.endTime,
                                repeatDays = state.selectDays.toRepeatDaysBinary(),
                                lockApplications = state.selectApps.toList(),
                                isEnabled = state.isEnabled,
                            )
                        routineDao.update(
                            RoutineEntity(
                                id = it,
                                name = state.name,
                                startTime = state.startTime,
                                endTime = state.endTime,
                                repeatDays = state.selectDays,
                                lockApplications = state.selectApps.toList(),
                                isEnabled = state.isEnabled,
                            ),
                        )
                        routineScheduler.cancelRoutine(id)
                        if (state.isEnabled) {
                            routineScheduler.scheduleRoutine(routineModel)
                        }

//                routineService.updateRoutine(
//                    id = it,
//                    updateRoutineRequest = UpdateRoutineRequest(
//                        name = state.name,
//                        startTime = state.startTime.toString(),
//                        endTime = state.endTime.toString(),
//                        repeatDays = state.selectDays.toRepeatDaysBinary(),
//                        lockApplications = state.selectApps.toList(),
//                    )
//                )
                    }.onSuccess {
//                postSideEffect(RoutineBottomSheetSideEffect.UpdateRoutineSuccess(
//                    RoutineModel(
//                        id = id,
//                        name = state.name,
//                        startTime = state.startTime,
//                        endTime = state.endTime,
//                        repeatDays = state.selectDays.toRepeatDaysBinary(),
//                        lockApplications = state.selectApps.toSet(),
//                        isEnabled = it.isEnabled,
//                    )
//                ))
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
)

sealed class RoutineBottomSheetSideEffect {
    data class AddRoutineSuccess(
        val routineModel: RoutineModel,
    ) : RoutineBottomSheetSideEffect()

    data class UpdateRoutineSuccess(
        val routineModel: RoutineModel,
    ) : RoutineBottomSheetSideEffect()
}
