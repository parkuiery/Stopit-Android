package com.uiery.keep.feature.routine

import androidx.datastore.preferences.core.emptyPreferences
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.datastore.RoutineNoticeStore
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class RoutineViewModelTemplateShareTest {
    @Test
    fun shareRoutineTemplatePostsPrivacySafePayloadAndTracksTap() = runBlocking {
        val analytics = RecordingRoutineShareAnalytics()
        val routine = routineEntity(
            name = "Study sprint",
            repeatDays = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            lockApplications = listOf("com.youtube.android"),
        )
        val viewModel = createViewModel(
            routineDao = FlowRoutineDao(listOf(routine)),
            analytics = analytics,
        )
        awaitState(viewModel) { it.routines.isNotEmpty() }

        viewModel.shareRoutineTemplate(routine.id)
        val effect = viewModel.container.sideEffectFlow.first()

        require(effect is RoutineSideEffect.ShareRoutineTemplate)
        assertEquals("study", effect.payload.templateCategory.analyticsValue)
        assertEquals("weekday", effect.payload.repeatDaysBucket.analyticsValue)
        assertEquals("evening", effect.payload.timeWindowBucket.analyticsValue)
        assertEquals(false, effect.payload.routineNameIncluded)
        assertEquals(
            listOf(RoutineShareEvent.Tapped("study", "weekday", "evening", false)),
            analytics.events,
        )
        assertEquals(false, effect.payload.text.contains("com.youtube.android"))
    }

    @Test
    fun shareRoutineTemplateInvalidRoutineTracksInvalidTemplateFailureWithoutSideEffect() = runBlocking {
        val analytics = RecordingRoutineShareAnalytics()
        val routine = routineEntity(repeatDays = emptyList())
        val viewModel = createViewModel(
            routineDao = FlowRoutineDao(listOf(routine)),
            analytics = analytics,
        )
        awaitState(viewModel) { it.routines.isNotEmpty() }

        viewModel.shareRoutineTemplate(routine.id)
        delay(100)

        assertEquals(
            listOf(RoutineShareEvent.Failed("custom", "invalid_template")),
            analytics.events,
        )
    }

    private fun createViewModel(
        routineDao: RoutineDao,
        analytics: KeepAnalytics,
    ): RoutineViewModel {
        val dataStore = FakeDataStore(emptyPreferences())
        val scheduler = Mockito.mock(RoutineScheduler::class.java).also {
            Mockito.`when`(it.canScheduleExactAlarms()).thenReturn(true)
        }
        return RoutineViewModel(
            routineDao = routineDao,
            dataStore = dataStore,
            analytics = analytics,
            exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(scheduler),
            routineNoticeStore = RoutineNoticeStore(dataStore),
        )
    }

    private suspend fun awaitState(
        viewModel: RoutineViewModel,
        predicate: (RoutineUiState) -> Boolean,
    ): RoutineUiState {
        repeat(20) {
            val state = viewModel.container.stateFlow.value
            if (predicate(state)) return state
            delay(10)
        }
        return viewModel.container.stateFlow.value
    }

    private fun routineEntity(
        id: Long = 7L,
        name: String = "Study routine",
        repeatDays: List<DayOfWeek> = listOf(DayOfWeek.MONDAY),
        startTime: LocalTime = LocalTime(hour = 19, minute = 0),
        endTime: LocalTime = LocalTime(hour = 21, minute = 0),
        lockApplications: List<String> = listOf("com.example.blocked"),
    ) = RoutineModel(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        repeatDays = repeatDays.toRepeatDaysBinary(),
        lockApplications = lockApplications,
        isEnabled = true,
    ).toEntity()
}

private class FlowRoutineDao(
    routines: List<RoutineEntity>,
) : RoutineDao {
    private val state = MutableStateFlow(routines)
    override fun fetchAll(): Flow<List<RoutineEntity>> = state
    override fun fetchAllOnce(): List<RoutineEntity> = state.value
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id
    override fun deleteById(id: Long) = Unit
    override fun update(routineEntity: RoutineEntity) = Unit
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private class RecordingRoutineShareAnalytics : KeepAnalytics {
    val events = mutableListOf<RoutineShareEvent>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackRoutineTemplateShareTapped(
        templateCategory: String,
        repeatDaysBucket: String,
        timeWindowBucket: String,
        routineNameIncluded: Boolean,
    ) {
        events += RoutineShareEvent.Tapped(
            templateCategory = templateCategory,
            repeatDaysBucket = repeatDaysBucket,
            timeWindowBucket = timeWindowBucket,
            routineNameIncluded = routineNameIncluded,
        )
    }

    override fun trackRoutineTemplateShareFailed(templateCategory: String, reason: String) {
        events += RoutineShareEvent.Failed(templateCategory, reason)
    }
}

private sealed interface RoutineShareEvent {
    data class Tapped(
        val templateCategory: String,
        val repeatDaysBucket: String,
        val timeWindowBucket: String,
        val routineNameIncluded: Boolean,
    ) : RoutineShareEvent

    data class Failed(
        val templateCategory: String,
        val reason: String,
    ) : RoutineShareEvent
}
