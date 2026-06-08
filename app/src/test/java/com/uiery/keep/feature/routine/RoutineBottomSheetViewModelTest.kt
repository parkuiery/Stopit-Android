package com.uiery.keep.feature.routine

import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek

class RoutineBottomSheetViewModelTest {
    @Test
    fun resetEditStateCopiesRoutineEnabledState() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine(isEnabled = false))
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertFalse(state.isEnabled)
    }

    @Test
    fun resetEditStateEnablesSaveButtonForValidExistingRoutine() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine())
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertTrue(state.isButtonEnable)
    }

    @Test
    fun resetEditStateDisablesSaveButtonForInvalidExistingRoutine() = runBlocking {
        val viewModel = createViewModel()

        viewModel.resetEditState(validRoutine(lockApplications = emptyList()))
        val state = awaitState(viewModel) { it.name == "Morning focus" }

        assertFalse(state.isButtonEnable)
    }

    @Test
    fun editRoutineWithDisabledRoutineUpdatesDisabledEntityAndDoesNotScheduleAlarm() = runBlocking {
        listOf(true, false).forEach { canScheduleExactAlarms ->
            val routineDao = RecordingRoutineDao()
            val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
            Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(canScheduleExactAlarms)
            Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
                .thenReturn(RoutineScheduleResult.Scheduled)
            val viewModel = createViewModel(
                routineDao = routineDao,
                routineScheduler = routineScheduler,
            )

            viewModel.resetEditState(validRoutine(id = 7L, isEnabled = false))
            awaitState(viewModel) { it.name == "Morning focus" }
            viewModel.editRoutine(7L)
            awaitUntil { routineDao.updatedEntity != null }

            assertEquals(false, routineDao.updatedEntity?.isEnabled)
            Mockito.verify(routineScheduler).cancelRoutine(7L)
            Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        }
    }

    @Test
    fun editRoutinePersistsThroughBottomSheetPathAndTracksScheduledRoutine() = runBlocking {
        val routineDao = RecordingRoutineDao()
        val analytics = RecordingKeepAnalytics()
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
            analytics = analytics,
        )

        viewModel.resetEditState(validRoutine(id = 7L))
        awaitState(viewModel) { it.name == "Morning focus" }
        viewModel.editRoutine(7L)
        awaitUntil { routineDao.updatedEntity != null }
        awaitUntil { analytics.lockScheduledCalls.isNotEmpty() }

        assertEquals(7L, routineDao.updatedEntity?.id)
        Mockito.verify(routineScheduler, Mockito.timeout(1_000)).cancelRoutine(7L)
        Mockito.verify(routineScheduler, Mockito.timeout(1_000)).scheduleRoutine(anyRoutine())
        assertEquals(
            listOf(AnalyticsScheduleType.ROUTINE to 30L),
            analytics.lockScheduledCalls,
        )
    }

    @Test
    fun addRoutinePersistsThroughBottomSheetPathAndTracksScheduledRoutine() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 42L)
        val analytics = RecordingKeepAnalytics()
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
            analytics = analytics,
        )

        fillValidRoutine(viewModel)
        viewModel.addRoutine()
        awaitUntil { routineDao.insertedEntity != null }

        assertEquals("Morning focus", routineDao.insertedEntity?.name)
        Mockito.verify(routineScheduler).scheduleRoutine(anyRoutine())
        assertEquals(
            listOf(AnalyticsScheduleType.ROUTINE to 30L),
            analytics.lockScheduledCalls,
        )
    }

    @Test
    fun addRoutineWithMissingExactAlarmPermissionStoresDisabledRoutineAndRequestsPermission() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 43L)
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(false)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
        )
        val sideEffect = async { viewModel.container.sideEffectFlow.first() }

        fillValidRoutine(viewModel)
        viewModel.addRoutine()
        awaitUntil { routineDao.insertedEntity != null }

        assertEquals(false, routineDao.insertedEntity?.isEnabled)
        Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        assertEquals(RoutineBottomSheetSideEffect.ShowAlarmPermission, sideEffect.await())
    }

    @Test
    fun repeatBlockSuggestionPrefillsEditableRoutineFieldsAndTracksAppliedAfterSave() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 44L)
        val analytics = RecordingKeepAnalytics()
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
            analytics = analytics,
        )
        val suggestion = repeatBlockSuggestion()

        viewModel.applyRepeatBlockRoutineSuggestionPrefill(
            surface = RepeatBlockRoutineSuggestionSurface.HOME,
            suggestion = suggestion,
        )
        val prefilled = awaitState(viewModel) { it.selectApps == suggestion.prefillPackages.toSet() }

        assertEquals("", prefilled.name)
        assertEquals(LocalTime(hour = 22, minute = 0), prefilled.startTime)
        assertEquals(LocalTime(hour = 0, minute = 0), prefilled.endTime)
        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            prefilled.selectDays,
        )
        assertFalse(prefilled.isButtonEnable)
        val expectedAnalyticsPayload = suggestion.toExpectedAnalyticsPayload()
        assertEquals(
            listOf(RepeatBlockRoutineSuggestionSurface.HOME to expectedAnalyticsPayload),
            analytics.repeatBlockClickedCalls,
        )

        viewModel.setName("Sleep defense")
        awaitState(viewModel) { it.isButtonEnable }
        viewModel.addRoutine()
        awaitUntil { routineDao.insertedEntity != null }

        assertEquals(suggestion.prefillPackages, routineDao.insertedEntity?.lockApplications)
        assertEquals(
            listOf(RepeatBlockRoutineSuggestionSurface.HOME to expectedAnalyticsPayload),
            analytics.repeatBlockAppliedCalls,
        )
    }

    private fun createViewModel(
        routineDao: RoutineDao = RecordingRoutineDao(),
        routineScheduler: RoutineScheduler = Mockito.mock(RoutineScheduler::class.java).also {
            Mockito.`when`(it.canScheduleExactAlarms()).thenReturn(true)
        },
        analytics: KeepAnalytics = NoOpKeepAnalytics(),
    ) = RoutineBottomSheetViewModel(
        routineRepository = RoomRoutineRepository(routineDao),
        exactAlarmOrchestrator = RoutineExactAlarmOrchestrator(routineScheduler),
        analytics = analytics,
    )

    private suspend fun fillValidRoutine(viewModel: RoutineBottomSheetViewModel) {
        viewModel.setName("Morning focus")
        viewModel.setStartTime(LocalTime(hour = 9, minute = 0))
        viewModel.setEndTime(LocalTime(hour = 9, minute = 30))
        viewModel.setSelectDays(DayOfWeek.MONDAY)
        viewModel.setSelectApps(setOf("com.example.blocked"))
        awaitState(viewModel) { it.isButtonEnable }
    }

    private suspend fun awaitState(
        viewModel: RoutineBottomSheetViewModel,
        predicate: (RoutineBottomSheetUiState) -> Boolean,
    ): RoutineBottomSheetUiState {
        repeat(20) {
            val state = viewModel.container.stateFlow.value
            if (predicate(state)) return state
            delay(10)
        }
        return viewModel.container.stateFlow.value
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(20) {
            if (predicate()) return
            delay(10)
        }
    }

    private fun anyRoutine(): RoutineModel =
        Mockito.any(RoutineModel::class.java) ?: validRoutine()

    private fun validRoutine(
        id: Long = 1L,
        isEnabled: Boolean = true,
        lockApplications: List<String> = listOf("com.example.blocked"),
    ) = RoutineModel(
        id = id,
        name = "Morning focus",
        startTime = LocalTime(hour = 9, minute = 0),
        endTime = LocalTime(hour = 9, minute = 30),
        repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
        lockApplications = lockApplications,
        isEnabled = isEnabled,
        changeLockHours = 2,
    )

    private fun repeatBlockSuggestion() = RepeatBlockRoutineSuggestion(
        reason = RepeatBlockSuggestionReason.RepeatBlockTimeBucket,
        timeBucket = RepeatBlockTimeBucket.Night,
        dayType = RepeatBlockDayType.Weekday,
        categoryBucket = RepeatBlockCategoryBucket.Social,
        repeatCountBucket = RepeatBlockCountBucket.ThreeToFive,
        routineCoverageState = RoutineCoverageState.NotCovered,
        prefillPackages = listOf("com.instagram.android", "com.twitter.android"),
        prefillStartTime = LocalTime(hour = 22, minute = 0),
        prefillEndTime = LocalTime(hour = 0, minute = 0),
    )
}

private class RecordingRoutineDao(
    private val insertedId: Long = 1L,
) : RoutineDao {
    var insertedEntity: RoutineEntity? = null
    var updatedEntity: RoutineEntity? = null

    override fun fetchAll(): Flow<List<RoutineEntity>> = emptyFlow()
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = throw UnsupportedOperationException()

    override fun insert(routineEntity: RoutineEntity): Long {
        insertedEntity = routineEntity.copy(id = insertedId)
        return insertedId
    }

    override fun deleteById(id: Long) = Unit

    override fun update(routineEntity: RoutineEntity) {
        updatedEntity = routineEntity
    }

    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private open class NoOpKeepAnalytics : KeepAnalytics {
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
}

private class RecordingKeepAnalytics : NoOpKeepAnalytics() {
    val lockScheduledCalls = mutableListOf<Pair<String, Long>>()
    val repeatBlockClickedCalls = mutableListOf<Pair<String, RepeatBlockRoutineSuggestionAnalyticsPayload>>()
    val repeatBlockAppliedCalls = mutableListOf<Pair<String, RepeatBlockRoutineSuggestionAnalyticsPayload>>()

    override fun trackLockScheduled(scheduleType: String, scheduledDurationMinutes: Long) {
        lockScheduledCalls += scheduleType to scheduledDurationMinutes
    }

    override fun trackRepeatBlockRoutineSuggestionClicked(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) {
        repeatBlockClickedCalls += surface to suggestion
    }

    override fun trackRepeatBlockRoutineSuggestionApplied(
        surface: String,
        suggestion: RepeatBlockRoutineSuggestionAnalyticsPayload,
    ) {
        repeatBlockAppliedCalls += surface to suggestion
    }
}

private fun RepeatBlockRoutineSuggestion.toExpectedAnalyticsPayload() = RepeatBlockRoutineSuggestionAnalyticsPayload(
    reason = reason.analyticsValue,
    timeBucket = timeBucket.analyticsValue,
    dayType = dayType.analyticsValue,
    categoryBucket = categoryBucket.analyticsValue,
    repeatCountBucket = repeatCountBucket.analyticsValue,
    routineCoverageState = routineCoverageState.analyticsValue,
)
