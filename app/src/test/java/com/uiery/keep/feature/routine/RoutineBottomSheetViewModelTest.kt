package com.uiery.keep.feature.routine

import com.uiery.keep.data.routine.RoomRoutineRepository
import com.uiery.keep.data.routine.RoutineExactAlarmOrchestrator
import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.analytics.AnalyticsScheduleType
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionAnalyticsPayload
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.analytics.routine.RoutineSavedAnalyticsPayload
import com.uiery.keep.analytics.routine.RoutineSavedCreationSource
import com.uiery.keep.analytics.routine.RoutineSavedScheduleState
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.database.mapper.toEntity
import com.uiery.keep.domain.repeatblock.RepeatBlockCategoryBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockCountBucket
import com.uiery.keep.domain.repeatblock.RepeatBlockDayType
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.repeatblock.RepeatBlockSuggestionReason
import com.uiery.keep.domain.repeatblock.RepeatBlockTimeBucket
import com.uiery.keep.domain.repeatblock.RoutineCoverageState
import com.uiery.keep.notification.RoutineScheduleResult
import com.uiery.keep.notification.RoutineScheduler
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.time.DayOfWeek
import java.time.LocalDateTime

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
            val routineDao = RecordingRoutineDao(initialEntities = listOf(validRoutine(id = 7L, isEnabled = false).toEntity()))
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
            awaitUntil("disabled routine DAO update") { routineDao.updatedEntity != null }

            assertEquals(false, routineDao.updatedEntity?.isEnabled)
            awaitRoutineCancelled(routineScheduler, 7L)
            Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        }
    }

    @Test
    fun awaitUntilReportsWhichRoutineBottomSheetPredicateTimedOut() = runBlocking {
        val error = runCatching {
            awaitUntil("analytics lockScheduledCalls", timeoutMillis = 1) { false }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(
            "Timed out waiting for RoutineBottomSheetViewModel predicate: analytics lockScheduledCalls",
            error?.message,
        )
    }

    @Test
    fun editRoutinePersistsThroughBottomSheetPathAndTracksScheduledRoutine() = runBlocking {
        val routineDao = RecordingRoutineDao(initialEntities = listOf(validRoutine(id = 7L).copy(changeLockHours = 0).toEntity()))
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
        awaitUntil("edited routine DAO update") { routineDao.updatedEntity != null }
        awaitUntil("edited routine lockScheduled analytics") { analytics.lockScheduledCalls.isNotEmpty() }

        assertEquals(7L, routineDao.updatedEntity?.id)
        awaitRoutineCancelled(routineScheduler, 7L)
        awaitRoutineScheduled(routineScheduler)
        assertEquals(
            listOf(AnalyticsScheduleType.ROUTINE to 30L),
            analytics.lockScheduledCalls,
        )
    }

    @Test
    fun editRoutineRechecksStoredRoutineAndBlocksSaveWhenRoutineBecameActiveAfterSheetOpened() = runBlocking {
        val activeRoutine = activeRoutine(id = 7L)
        val routineDao = RecordingRoutineDao(initialEntities = listOf(activeRoutine.toEntity()))
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(true)
        Mockito.`when`(routineScheduler.scheduleRoutine(anyRoutine()))
            .thenReturn(RoutineScheduleResult.Scheduled)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
        )
        val sideEffect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.resetEditState(activeRoutine.copy(name = "Opened before active"))
        awaitState(viewModel) { it.name == "Opened before active" }
        viewModel.setName("Bypass attempt")
        viewModel.editRoutine(activeRoutine.id)

        assertEquals(RoutineBottomSheetSideEffect.ShowActiveRoutineBlocked, sideEffect.await())
        assertEquals(null, routineDao.updatedEntity)
        Mockito.verify(routineScheduler, Mockito.never()).cancelRoutine(activeRoutine.id)
        Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        Unit
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

        val sideEffects = async { viewModel.container.sideEffectFlow.take(1).toList() }

        fillValidRoutine(viewModel)
        viewModel.addRoutine()
        awaitUntil("added routine DAO insert") { routineDao.insertedEntity != null }

        assertEquals("Morning focus", routineDao.insertedEntity?.name)
        awaitRoutineScheduled(routineScheduler)
        awaitUntil("added routine lockScheduled analytics") { analytics.lockScheduledCalls.isNotEmpty() }
        assertEquals(
            listOf(AnalyticsScheduleType.ROUTINE to 30L),
            analytics.lockScheduledCalls,
        )
        assertEquals(
            listOf(
                RoutineSavedAnalyticsPayload(
                    entrySurface = "routine",
                    creationSource = RoutineSavedCreationSource.MANUAL,
                    selectedAppCountBucket = "1",
                    repeatDaysBucket = "1",
                    timeWindowBucket = "morning",
                    scheduleState = RoutineSavedScheduleState.ENABLED,
                ),
            ),
            analytics.routineSavedCalls,
        )
        assertEquals(
            listOf(RoutineBottomSheetSideEffect.CloseBottomSheet),
            withTimeout(1_000) { sideEffects.await() },
        )
    }

    @Test
    fun sameStartAndEndTimeDisablesSaveAndDoesNotPersistOrTrackAllDayRoutine() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 46L)
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
        viewModel.setEndTime(LocalTime(hour = 9, minute = 0))
        val invalidState = awaitState(viewModel) { !it.isButtonEnable }
        viewModel.addRoutine()
        delay(100)

        assertEquals(LocalTime(hour = 9, minute = 0), invalidState.startTime)
        assertEquals(LocalTime(hour = 9, minute = 0), invalidState.endTime)
        assertEquals(null, routineDao.insertedEntity)
        Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        assertTrue(analytics.lockScheduledCalls.isEmpty())
        assertTrue(analytics.routineSavedCalls.isEmpty())
    }

    @Test
    fun overnightRoutineLongerThanMinimumRemainsValid() = runBlocking {
        val viewModel = createViewModel()

        fillValidRoutine(viewModel)
        viewModel.setStartTime(LocalTime(hour = 23, minute = 0))
        viewModel.setEndTime(LocalTime(hour = 1, minute = 0))
        val validState = awaitState(viewModel, "overnight routine time update") {
            it.startTime == LocalTime(hour = 23, minute = 0) &&
                it.endTime == LocalTime(hour = 1, minute = 0) &&
                it.isButtonEnable
        }

        assertEquals(LocalTime(hour = 23, minute = 0), validState.startTime)
        assertEquals(LocalTime(hour = 1, minute = 0), validState.endTime)
    }

    @Test
    fun addRoutineWithMissingExactAlarmPermissionStoresDisabledRoutineAndRequestsPermissionBeforeClosingSheet() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 43L)
        val analytics = RecordingKeepAnalytics()
        val routineScheduler = Mockito.mock(RoutineScheduler::class.java)
        Mockito.`when`(routineScheduler.canScheduleExactAlarms()).thenReturn(false)
        val viewModel = createViewModel(
            routineDao = routineDao,
            routineScheduler = routineScheduler,
            analytics = analytics,
        )
        val sideEffects = async { viewModel.container.sideEffectFlow.take(2).toList() }

        fillValidRoutine(viewModel)
        viewModel.addRoutine()
        awaitUntil("exact-alarm missing routine DAO insert") { routineDao.insertedEntity != null }

        assertEquals(false, routineDao.insertedEntity?.isEnabled)
        Mockito.verify(routineScheduler, Mockito.never()).scheduleRoutine(anyRoutine())
        assertEquals(
            listOf(RoutineSavedScheduleState.DISABLED_EXACT_ALARM_MISSING),
            analytics.routineSavedCalls.map { it.scheduleState },
        )
        assertEquals(
            listOf(
                RoutineBottomSheetSideEffect.ShowAlarmPermission,
                RoutineBottomSheetSideEffect.CloseBottomSheet,
            ),
            withTimeout(1_000) { sideEffects.await() },
        )
    }

    @Test
    fun addRoutineFromHomeRoutineCreationCtaTracksPostFirstBlockAttribution() = runBlocking {
        val routineDao = RecordingRoutineDao(insertedId = 45L)
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

        viewModel.resetState(
            routineSavedEntrySurface = "home_secondary",
            routineSavedCreationSource = RoutineSavedCreationSource.POST_FIRST_BLOCK_CTA,
        )
        awaitUntil("home CTA routine attribution state") {
            viewModel.container.stateFlow.value.routineSavedEntrySurface == "home_secondary" &&
                viewModel.container.stateFlow.value.routineSavedCreationSource ==
                RoutineSavedCreationSource.POST_FIRST_BLOCK_CTA
        }
        fillValidRoutine(viewModel)
        viewModel.addRoutine()
        awaitUntil("home CTA routine DAO insert") { routineDao.insertedEntity != null }
        awaitUntil("home CTA routineSaved analytics") { analytics.routineSavedCalls.isNotEmpty() }

        assertEquals(
            listOf(
                RoutineSavedAnalyticsPayload(
                    entrySurface = "home_secondary",
                    creationSource = RoutineSavedCreationSource.POST_FIRST_BLOCK_CTA,
                    selectedAppCountBucket = "1",
                    repeatDaysBucket = "1",
                    timeWindowBucket = "morning",
                    scheduleState = RoutineSavedScheduleState.ENABLED,
                ),
            ),
            analytics.routineSavedCalls.toList(),
        )
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
        assertEquals(1, analytics.repeatBlockClickedCalls.size)
        assertEquals(RepeatBlockRoutineSuggestionSurface.HOME, analytics.repeatBlockClickedCalls.single().first)
        assertEquals(expectedAnalyticsPayload, analytics.repeatBlockClickedCalls.single().second)

        viewModel.setName("Sleep defense")
        awaitState(viewModel) { it.isButtonEnable }
        viewModel.addRoutine()
        awaitUntil("repeat-block routine DAO insert") { routineDao.insertedEntity != null }

        assertEquals(suggestion.prefillPackages, routineDao.insertedEntity?.lockApplications)
        awaitUntil("repeat-block applied and routineSaved analytics") {
            analytics.repeatBlockAppliedCalls.isNotEmpty() && analytics.routineSavedCalls.isNotEmpty()
        }
        assertEquals(1, analytics.repeatBlockAppliedCalls.size)
        assertEquals(RepeatBlockRoutineSuggestionSurface.HOME, analytics.repeatBlockAppliedCalls.single().first)
        assertEquals(expectedAnalyticsPayload, analytics.repeatBlockAppliedCalls.single().second)
        assertEquals(
            listOf(
                RoutineSavedAnalyticsPayload(
                    entrySurface = RepeatBlockRoutineSuggestionSurface.HOME,
                    creationSource = RoutineSavedCreationSource.REPEAT_BLOCK_PREFILL,
                    selectedAppCountBucket = "2_3",
                    repeatDaysBucket = "4_6",
                    timeWindowBucket = "overnight",
                    scheduleState = RoutineSavedScheduleState.ENABLED,
                ),
            ),
            analytics.routineSavedCalls,
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
        description: String = "state predicate",
        predicate: (RoutineBottomSheetUiState) -> Boolean,
    ): RoutineBottomSheetUiState {
        awaitUntil(description) { predicate(viewModel.container.stateFlow.value) }
        return viewModel.container.stateFlow.value
    }

    private suspend fun awaitUntil(
        description: String,
        timeoutMillis: Long = 2_000,
        predicate: () -> Boolean,
    ) {
        runCatching {
            withTimeout(timeoutMillis) {
                while (true) {
                    if (predicate()) return@withTimeout
                    delay(10)
                }
            }
        }.getOrElse {
            error("Timed out waiting for RoutineBottomSheetViewModel predicate: $description")
        }
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        awaitUntil("asynchronous RoutineBottomSheetViewModel predicate", predicate = predicate)
    }

    private suspend fun awaitRoutineCancelled(
        routineScheduler: RoutineScheduler,
        id: Long,
    ) {
        awaitUntil("routine $id cancellation") {
            runCatching {
                Mockito.verify(routineScheduler).cancelRoutine(id)
            }.isSuccess
        }
    }

    private suspend fun awaitRoutineScheduled(routineScheduler: RoutineScheduler) {
        awaitUntil("routine schedule") {
            runCatching {
                Mockito.verify(routineScheduler).scheduleRoutine(anyRoutine())
            }.isSuccess
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

    private fun activeRoutine(id: Long = 1L): RoutineModel {
        val now = LocalDateTime.now()
        val start = now.minusMinutes(5)
        val end = now.plusMinutes(55)
        return validRoutine(id = id).copy(
            startTime = LocalTime(hour = start.hour, minute = start.minute),
            endTime = LocalTime(hour = end.hour, minute = end.minute),
            repeatDays = listOf(now.dayOfWeek).toRepeatDaysBinary(),
            changeLockHours = 0,
        )
    }

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
    initialEntities: List<RoutineEntity> = emptyList(),
) : RoutineDao {
    var insertedEntity: RoutineEntity? = null
    var updatedEntity: RoutineEntity? = null
    private val entities = initialEntities.associateBy { it.id }.toMutableMap()

    override fun fetchAll(): Flow<List<RoutineEntity>> = emptyFlow()
    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()
    override fun fetch(id: Long): RoutineEntity = entities[id] ?: recordingRoutineEntity(id)

    override fun insert(routineEntity: RoutineEntity): Long {
        insertedEntity = routineEntity.copy(id = insertedId)
        return insertedId
    }

    override fun deleteById(id: Long) = Unit

    override fun update(routineEntity: RoutineEntity) {
        updatedEntity = routineEntity
        entities[routineEntity.id] = routineEntity
    }

    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}

private fun recordingRoutineEntity(id: Long) = RoutineEntity(
    id = id,
    name = "Morning focus",
    startTime = LocalTime(hour = 9, minute = 0),
    endTime = LocalTime(hour = 9, minute = 30),
    repeatDays = listOf(DayOfWeek.MONDAY),
    lockApplications = listOf("com.example.blocked"),
    isEnabled = true,
    changeLockHours = 2,
)

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
    val routineSavedCalls = mutableListOf<RoutineSavedAnalyticsPayload>()

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

    override fun trackRoutineSaved(payload: RoutineSavedAnalyticsPayload) {
        routineSavedCalls += payload
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
