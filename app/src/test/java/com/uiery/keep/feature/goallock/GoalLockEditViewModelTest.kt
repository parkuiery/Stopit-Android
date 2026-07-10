package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.AnalyticsGoalLockChangedField
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class GoalLockEditViewModelTest {
    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun loadInitializesExactDraftAndLogsScreen() = runBlocking {
        val scheduled = GoalLockMode.Scheduled(
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startTime = LocalTime.of(18, 30),
            endTime = LocalTime.of(22, 0),
        )
        val fixture = goalLock(lockMode = scheduled, selectedPackages = setOf(" com.b ", "com.a"))
        val dao = EditGoalLockDao(fixture)
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)

        viewModel.loadGoalLock(today)
        awaitUntil { !viewModel.container.stateFlow.value.isLoading }

        val state = viewModel.container.stateFlow.value
        assertEquals(fixture.goalName, state.goalName)
        assertEquals(fixture.startDate, state.startDate)
        assertEquals(fixture.endDate, state.endDate)
        assertEquals(scheduled, state.lockMode)
        assertEquals(setOf("com.a", "com.b"), state.selectedPackages)
        assertFalse(state.isDirty)
        assertFalse(state.canSave)
        assertEquals(listOf(KeepAnalyticsScreen.GOAL_LOCK_EDIT), analytics.screenViews)
    }

    @Test
    fun missingAndTerminalGoalsLeaveEdit() = runBlocking {
        val missingViewModel = viewModel(EditGoalLockDao(null), EditAnalytics())
        val missingEffect = async { missingViewModel.container.sideEffectFlow.first() }
        missingViewModel.loadGoalLock(today)
        assertEquals(GoalLockEditSideEffect.NotFound, missingEffect.await())

        val endedViewModel = viewModel(
            EditGoalLockDao(goalLock(status = GoalLockStoredStatus.EndedEarly)),
            EditAnalytics(),
        )
        val endedEffect = async { endedViewModel.container.sideEffectFlow.first() }
        endedViewModel.loadGoalLock(today)
        assertEquals(GoalLockEditSideEffect.Unavailable, endedEffect.await())
    }

    @Test
    fun expiredGoalCompletesBeforeLeavingEdit() = runBlocking {
        val dao = EditGoalLockDao(goalLock(endDate = today.minusDays(1)))
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.loadGoalLock(today)

        assertEquals(GoalLockEditSideEffect.Unavailable, effect.await())
        assertEquals(GoalLockStoredStatus.Completed, dao.updates.single().status)
        assertEquals(1, analytics.completedCalls)
    }

    @Test
    fun loadFailureIsRetryableAndDoesNotTrackCompletion() = runBlocking {
        val dao = EditGoalLockDao(goalLock()).apply { fetchFailuresRemaining = 1 }
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)

        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.error == GoalLockEditError.Load }

        assertEquals(0, analytics.completedCalls)
        viewModel.retryLoad()
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        assertNull(viewModel.container.stateFlow.value.error)
    }

    @Test
    fun mutationsDeriveValidationDirtyStateAndDiscardPolicy() = runBlocking {
        val customSchedule = GoalLockMode.Scheduled(
            repeatDays = setOf(DayOfWeek.SATURDAY),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(12, 0),
        )
        val viewModel = viewModel(EditGoalLockDao(goalLock(lockMode = customSchedule)), EditAnalytics())
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }

        assertEquals(customSchedule, viewModel.container.stateFlow.value.lockMode)
        viewModel.setAllDayMode()
        awaitUntil { viewModel.container.stateFlow.value.lockMode == GoalLockMode.AllDay }
        viewModel.setWeekdayEveningMode()
        awaitUntil { viewModel.container.stateFlow.value.lockMode is GoalLockMode.Scheduled }
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            (viewModel.container.stateFlow.value.lockMode as GoalLockMode.Scheduled).repeatDays,
        )

        viewModel.setGoalName("   ")
        awaitUntil { !viewModel.container.stateFlow.value.isValid }
        assertFalse(viewModel.container.stateFlow.value.canSave)
        viewModel.setGoalName("  새 목표  ")
        viewModel.setEndDate(today.minusDays(1))
        awaitUntil { !viewModel.container.stateFlow.value.isValid }
        viewModel.setEndDate(today)
        viewModel.setSelectedApps(emptySet())
        awaitUntil { !viewModel.container.stateFlow.value.isValid }
        viewModel.setSelectedApps(setOf("com.new"))
        awaitUntil { viewModel.container.stateFlow.value.canSave }

        viewModel.requestBack()
        awaitUntil { viewModel.container.stateFlow.value.showDiscardConfirmation }
        viewModel.cancelDiscard()
        awaitUntil { !viewModel.container.stateFlow.value.showDiscardConfirmation }
        val effect = async { viewModel.container.sideEffectFlow.first() }
        viewModel.confirmDiscard()
        assertEquals(GoalLockEditSideEffect.NavigateBack, effect.await())
    }

    @Test
    fun durationIsAnchoredToImmutableStartAndPendingMayEndOnStart() = runBlocking {
        val start = today.plusDays(3)
        val viewModel = viewModel(
            EditGoalLockDao(goalLock(startDate = start, endDate = start.plusDays(6))),
            EditAnalytics(),
        )
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }

        viewModel.setDurationDays(14)
        awaitUntil { viewModel.container.stateFlow.value.endDate == start.plusDays(13) }
        assertEquals(start, viewModel.container.stateFlow.value.startDate)
        viewModel.setEndDate(start.minusDays(1))
        awaitUntil { !viewModel.container.stateFlow.value.isValid }
        viewModel.setEndDate(start)
        awaitUntil { viewModel.container.stateFlow.value.isValid }
    }

    @Test
    fun savePersistsOnceAndTracksOnlyChangedFieldsWithFinalMode() = runBlocking {
        val dao = EditGoalLockDao(goalLock())
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("새 목표")
        viewModel.setDurationDays(14)
        viewModel.setWeekdayEveningMode()
        viewModel.setSelectedApps(setOf("com.new"))
        awaitUntil { viewModel.container.stateFlow.value.canSave }
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.save(today)

        assertEquals(GoalLockEditSideEffect.Saved, effect.await())
        assertEquals(1, dao.updates.size)
        val updated = dao.updates.single()
        assertEquals(42L, updated.id)
        assertEquals(LocalDate.of(2026, 7, 4), updated.startDate)
        assertEquals(GoalLockStoredStatus.Active, updated.status)
        assertEquals("새 목표", updated.goalName)
        assertEquals(setOf("com.new"), updated.selectedPackages)
        assertEquals(
            setOf(
                AnalyticsGoalLockChangedField.NAME,
                AnalyticsGoalLockChangedField.DURATION,
                AnalyticsGoalLockChangedField.LOCK_MODE,
                AnalyticsGoalLockChangedField.APPS,
            ),
            analytics.updatedCalls.map { it.changedField }.toSet(),
        )
        assertTrue(analytics.updatedCalls.all { it.lockMode == AnalyticsGoalLockMode.SCHEDULED })
    }

    @Test
    fun duplicateSaveRequestsPersistOnlyOnce() = runBlocking {
        val dao = EditGoalLockDao(goalLock())
        val viewModel = viewModel(dao, EditAnalytics())
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("한 번만 저장")
        awaitUntil { viewModel.container.stateFlow.value.canSave }

        val firstSave = viewModel.save(today)
        val secondSave = viewModel.save(today)
        firstSave.join()
        secondSave.join()

        assertEquals(1, dao.updates.size)
    }

    @Test
    fun conditionalUpdateFailureCannotResurrectTerminalGoal() = runBlocking {
        val dao = EditGoalLockDao(goalLock()).apply { failConditionalUpdate = true }
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("변경")
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.save(today)

        assertEquals(GoalLockEditSideEffect.Unavailable, effect.await())
        assertTrue(dao.updates.isEmpty())
        assertTrue(analytics.updatedCalls.isEmpty())
    }

    @Test
    fun analyticsFailureDoesNotTurnCommittedSaveIntoFailure() = runBlocking {
        val dao = EditGoalLockDao(goalLock())
        val analytics = EditAnalytics().apply { throwOnUpdated = true }
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("저장 성공")
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.save(today)

        assertEquals(GoalLockEditSideEffect.Saved, effect.await())
        assertEquals("저장 성공", dao.updates.single().goalName)
        assertNull(viewModel.container.stateFlow.value.error)
    }

    @Test
    fun saveRejectsGoalThatBecameStoredTerminal() = runBlocking {
        val dao = EditGoalLockDao(goalLock())
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("변경")
        dao.existing = GoalLockEntity.fromDomain(
            goalLock(status = GoalLockStoredStatus.EndedEarly),
        )
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.save(today)

        assertEquals(GoalLockEditSideEffect.Unavailable, effect.await())
        assertTrue(dao.updates.isEmpty())
        assertTrue(analytics.updatedCalls.isEmpty())
    }

    @Test
    fun invalidLoadedScheduleDisablesSave() = runBlocking {
        val invalidSchedule = GoalLockMode.Scheduled(
            repeatDays = emptySet(),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(19, 0),
        )
        val viewModel = viewModel(
            EditGoalLockDao(goalLock(lockMode = invalidSchedule)),
            EditAnalytics(),
        )

        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("변경")
        awaitUntil { viewModel.container.stateFlow.value.isDirty }

        assertFalse(viewModel.container.stateFlow.value.isValid)
        assertFalse(viewModel.container.stateFlow.value.canSave)
    }

    @Test
    fun failedSaveRetainsDraftWithoutAnalyticsOrSavedEffect() = runBlocking {
        val dao = EditGoalLockDao(goalLock()).apply { updateFailuresRemaining = 1 }
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("저장 실패 목표")
        awaitUntil { viewModel.container.stateFlow.value.canSave }
        val effect = async {
            withTimeoutOrNull(200) { viewModel.container.sideEffectFlow.first() }
        }

        viewModel.save(today)
        awaitUntil { viewModel.container.stateFlow.value.error == GoalLockEditError.Save }

        assertEquals("저장 실패 목표", viewModel.container.stateFlow.value.goalName)
        assertTrue(analytics.updatedCalls.isEmpty())
        assertNull(effect.await())
    }

    @Test
    fun saveRejectsGoalThatExpiredWhileEditing() = runBlocking {
        val dao = EditGoalLockDao(goalLock(endDate = today))
        val analytics = EditAnalytics()
        val viewModel = viewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.originalGoal != null }
        viewModel.setGoalName("변경")
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.save(today.plusDays(1))

        assertEquals(GoalLockEditSideEffect.Unavailable, effect.await())
        assertEquals(GoalLockStoredStatus.Completed, dao.updates.single().status)
        assertTrue(analytics.updatedCalls.isEmpty())
        assertEquals(1, analytics.completedCalls)
    }

    private fun viewModel(
        dao: EditGoalLockDao,
        analytics: EditAnalytics,
    ) = GoalLockEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf(GOAL_LOCK_ID_ARG to 42L)),
        goalLockRepository = GoalLockRepository(dao),
        analytics = analytics,
    )

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        error("Condition was not met")
    }

    private fun goalLock(
        startDate: LocalDate = LocalDate.of(2026, 7, 4),
        endDate: LocalDate = LocalDate.of(2026, 7, 20),
        lockMode: GoalLockMode = GoalLockMode.AllDay,
        selectedPackages: Set<String> = setOf("com.one", "com.two"),
        status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
    ) = GoalLock(
        id = 42L,
        goalName = "시험 준비",
        startDate = startDate,
        endDate = endDate,
        lockMode = lockMode,
        selectedPackages = selectedPackages,
        status = status,
    )
}

private class EditGoalLockDao(existing: GoalLock?) : GoalLockDao {
    var existing: GoalLockEntity? = existing?.let(GoalLockEntity::fromDomain)
    var fetchFailuresRemaining = 0
    var updateFailuresRemaining = 0
    var failConditionalUpdate = false
    val updates = mutableListOf<GoalLock>()

    override fun fetchAll(): Flow<List<GoalLockEntity>> = emptyFlow()

    override fun fetch(id: Long): GoalLockEntity? {
        if (fetchFailuresRemaining > 0) {
            fetchFailuresRemaining--
            error("fetch failed")
        }
        return existing?.takeIf { it.id == id }
    }

    override fun insert(goalLock: GoalLockEntity): Long = error("insert should not be called")

    override fun update(goalLock: GoalLockEntity) {
        if (updateFailuresRemaining > 0) {
            updateFailuresRemaining--
            error("update failed")
        }
        existing = goalLock
        updates += goalLock.toDomain()
    }

    override fun updateIfActive(goalLock: GoalLockEntity): Boolean {
        if (failConditionalUpdate) {
            existing = existing?.copy(status = GoalLockEntity.STATUS_ENDED_EARLY)
            return false
        }
        val current = fetch(goalLock.id) ?: return false
        if (current.status != GoalLockEntity.STATUS_ACTIVE) return false
        update(goalLock)
        return true
    }
}

private data class EditUpdatedCall(val lockMode: String, val changedField: String)

private class EditAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val updatedCalls = mutableListOf<EditUpdatedCall>()
    var completedCalls = 0
    var throwOnUpdated = false

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) { screenViews += screenName }
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit

    override fun trackGoalLockCompleted(lockMode: String, durationDaysBucket: String) {
        completedCalls++
    }

    override fun trackGoalLockUpdated(lockMode: String, changedField: String) {
        if (throwOnUpdated) error("analytics failed")
        updatedCalls += EditUpdatedCall(lockMode, changedField)
    }
}
