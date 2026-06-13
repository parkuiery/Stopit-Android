package com.uiery.keep.feature.routine

import com.uiery.keep.data.routine.RoutineRepository
import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.AnalyticsSelectedAppCountBucket
import com.uiery.keep.analytics.AnalyticsSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.analytics.routine.RoutineSavedCreationSource
import com.uiery.keep.analytics.routine.RoutineSavedScheduleState
import com.uiery.keep.analytics.routine.RoutineTemplateRepeatDaysBucketName
import com.uiery.keep.analytics.routine.RoutineTemplateTimeWindowBucketName
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.isChangeLocked
import com.uiery.keep.util.isRunningNow
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
        private val routineRepository: RoutineRepository,
        private val exactAlarmOrchestrator: RoutineExactAlarmOrchestrator,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> {
        override val container: Container<RoutineBottomSheetUiState, RoutineBottomSheetSideEffect> =
            container(
                RoutineBottomSheetUiState(),
            )
        internal fun resetState(
            routineSavedEntrySurface: String? = null,
            routineSavedCreationSource: String? = null,
        ) =
            intent {
                reduce {
                    RoutineBottomSheetUiState(
                        routineSavedEntrySurface = routineSavedEntrySurface,
                        routineSavedCreationSource = routineSavedCreationSource,
                    )
                }
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
                            repeatBlockSuggestionPrefill = null,
                            repeatBlockSuggestionSurface = null,
                        )

                    editState.copy(isButtonEnable = editState.isValidForSave())
                }
            }

        internal fun applyRepeatBlockRoutineSuggestionPrefill(
            surface: String,
            suggestion: RepeatBlockRoutineSuggestion,
        ) = intent {
            analytics.trackRepeatBlockRoutineSuggestionClicked(
                surface = surface,
                suggestion = suggestion.toAnalyticsPayload(),
            )
            reduce {
                val prefilledState = state.copy(
                    startTime = suggestion.prefillStartTime,
                    endTime = suggestion.prefillEndTime,
                    selectDays = suggestion.dayType.toRoutinePrefillDays(),
                    selectApps = suggestion.prefillPackages.toSet(),
                    repeatBlockSuggestionPrefill = suggestion,
                    repeatBlockSuggestionSurface = surface,
                )
                prefilledState.copy(isButtonEnable = prefilledState.isValidForSave())
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
                val repeatBlockPrefill = state.repeatBlockSuggestionPrefill
                val repeatBlockSurface = state.repeatBlockSuggestionSurface
                val resolvedRoutine = exactAlarmOrchestrator.resolveBeforePersist(state.toRoutineModel())
                val insertedId = routineRepository.insert(resolvedRoutine.routine)
                val routineWithId = resolvedRoutine.routine.copy(id = insertedId)
                val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(routineWithId)

                if (scheduleDecision.routine != routineWithId) {
                    routineRepository.update(scheduleDecision.routine)
                }
                if (scheduleDecision.shouldTrackLockScheduled) {
                    analytics.trackLockScheduled(
                        scheduleType = AnalyticsScheduleType.ROUTINE,
                        scheduledDurationMinutes = routineDurationMinutes(routineWithId.startTime, routineWithId.endTime),
                    )
                }
                analytics.trackRoutineSaved(
                    state.toRoutineSavedAnalyticsPayload(
                        entrySurface = repeatBlockSurface ?: state.routineSavedEntrySurface ?: AnalyticsSource.ROUTINE,
                        creationSource = if (repeatBlockPrefill != null) {
                            RoutineSavedCreationSource.REPEAT_BLOCK_PREFILL
                        } else {
                            state.routineSavedCreationSource ?: RoutineSavedCreationSource.MANUAL
                        },
                        scheduleState = scheduleDecision.toRoutineSavedScheduleState(
                            permissionPromptRequested = resolvedRoutine.shouldShowPermissionPrompt,
                        ),
                    ),
                )
                if (repeatBlockPrefill != null && repeatBlockSurface != null) {
                    analytics.trackRepeatBlockRoutineSuggestionApplied(
                        surface = repeatBlockSurface,
                        suggestion = repeatBlockPrefill.toAnalyticsPayload(),
                    )
                    reduce {
                        state.copy(
                            repeatBlockSuggestionPrefill = null,
                            repeatBlockSuggestionSurface = null,
                            routineSavedEntrySurface = null,
                            routineSavedCreationSource = null,
                        )
                    }
                }
                if (resolvedRoutine.shouldShowPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt) {
                    postSideEffect(RoutineBottomSheetSideEffect.ShowAlarmPermission)
                }
                postSideEffect(RoutineBottomSheetSideEffect.CloseBottomSheet)
            }

        internal fun editRoutine(id: Long?) =
            intent {
                id?.let {
                    runCatching {
                        val storedRoutine = routineRepository.fetch(it)
                        if (storedRoutine.isRunningNow() || storedRoutine.isChangeLocked()) {
                            postSideEffect(RoutineBottomSheetSideEffect.ShowActiveRoutineBlocked)
                            return@runCatching
                        }

                        val resolvedRoutine = exactAlarmOrchestrator.resolveBeforePersist(state.toRoutineModel(id = it))
                        routineRepository.update(resolvedRoutine.routine)
                        exactAlarmOrchestrator.cancelRoutine(id)
                        val scheduleDecision = exactAlarmOrchestrator.scheduleEnabledRoutine(resolvedRoutine.routine)
                        if (scheduleDecision.routine != resolvedRoutine.routine) {
                            routineRepository.update(scheduleDecision.routine)
                        }
                        if (scheduleDecision.shouldTrackLockScheduled) {
                            analytics.trackLockScheduled(
                                scheduleType = AnalyticsScheduleType.ROUTINE,
                                scheduledDurationMinutes = routineDurationMinutes(
                                    resolvedRoutine.routine.startTime,
                                    resolvedRoutine.routine.endTime,
                                ),
                            )
                        }
                        if (resolvedRoutine.shouldShowPermissionPrompt || scheduleDecision.shouldShowPermissionPrompt) {
                            postSideEffect(RoutineBottomSheetSideEffect.ShowAlarmPermission)
                        }
                        postSideEffect(RoutineBottomSheetSideEffect.CloseBottomSheet)
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
    val repeatBlockSuggestionPrefill: RepeatBlockRoutineSuggestion? = null,
    val repeatBlockSuggestionSurface: String? = null,
    val routineSavedEntrySurface: String? = null,
    val routineSavedCreationSource: String? = null,
)

private fun RepeatBlockDayType.toRoutinePrefillDays(): List<DayOfWeek> = when (this) {
    RepeatBlockDayType.Weekday -> listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    RepeatBlockDayType.Weekend -> listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    RepeatBlockDayType.Daily -> DayOfWeek.entries
    RepeatBlockDayType.CustomDays -> emptyList()
}

private fun RoutineBottomSheetUiState.isValidForSave(): Boolean {
    val isNameValid = name.isNotEmpty()
    val isTimeValid = routineDurationMinutes(startTime, endTime) >= 15
    val isDaySelected = selectDays.isNotEmpty()
    return isNameValid && isTimeValid && isDaySelected && selectApps.isNotEmpty()
}

private fun RepeatBlockRoutineSuggestion.toAnalyticsPayload() = RepeatBlockRoutineSuggestionAnalyticsPayload(
    reason = reason.analyticsValue,
    timeBucket = timeBucket.analyticsValue,
    dayType = dayType.analyticsValue,
    categoryBucket = categoryBucket.analyticsValue,
    repeatCountBucket = repeatCountBucket.analyticsValue,
    routineCoverageState = routineCoverageState.analyticsValue,
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

private fun RoutineBottomSheetUiState.toRoutineSavedAnalyticsPayload(
    entrySurface: String,
    creationSource: String,
    scheduleState: String,
) = RoutineSavedAnalyticsPayload(
    entrySurface = entrySurface,
    creationSource = creationSource,
    selectedAppCountBucket = selectedAppCountBucket(selectApps.size),
    repeatDaysBucket = repeatDaysBucket(selectDays.toSet()),
    timeWindowBucket = timeWindowBucket(startTime, endTime),
    scheduleState = scheduleState,
)

private fun RoutineExactAlarmScheduleDecision.toRoutineSavedScheduleState(permissionPromptRequested: Boolean): String = when {
    shouldShowPermissionPrompt || permissionPromptRequested -> RoutineSavedScheduleState.DISABLED_EXACT_ALARM_MISSING
    routine.isEnabled -> RoutineSavedScheduleState.ENABLED
    else -> RoutineSavedScheduleState.DISABLED
}

private fun selectedAppCountBucket(count: Int): String = when (count) {
    1 -> AnalyticsSelectedAppCountBucket.ONE
    in 2..3 -> AnalyticsSelectedAppCountBucket.TWO_TO_THREE
    in 4..6 -> AnalyticsSelectedAppCountBucket.FOUR_TO_SIX
    else -> AnalyticsSelectedAppCountBucket.SEVEN_PLUS
}

private fun repeatDaysBucket(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> RoutineTemplateRepeatDaysBucketName.NONE
    days == weekdaySet -> RoutineTemplateRepeatDaysBucketName.WEEKDAY
    days == weekendSet -> RoutineTemplateRepeatDaysBucketName.WEEKEND
    days.size == DayOfWeek.entries.size -> RoutineTemplateRepeatDaysBucketName.DAILY
    else -> RoutineTemplateRepeatDaysBucketName.CUSTOM_DAYS
}

private val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)

private val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun timeWindowBucket(
    startTime: LocalTime,
    endTime: LocalTime,
): String {
    if (endTime <= startTime) return RoutineTemplateTimeWindowBucketName.OVERNIGHT
    return when (startTime.hour) {
        in 5..11 -> RoutineTemplateTimeWindowBucketName.MORNING
        in 12..16 -> RoutineTemplateTimeWindowBucketName.AFTERNOON
        in 17..20 -> RoutineTemplateTimeWindowBucketName.EVENING
        in 21..23, in 0..4 -> RoutineTemplateTimeWindowBucketName.NIGHT
        else -> RoutineTemplateTimeWindowBucketName.CUSTOM_WINDOW
    }
}

sealed interface RoutineBottomSheetSideEffect {
    data object ShowAlarmPermission : RoutineBottomSheetSideEffect
    data object ShowActiveRoutineBlocked : RoutineBottomSheetSideEffect
    data object CloseBottomSheet : RoutineBottomSheetSideEffect
}
