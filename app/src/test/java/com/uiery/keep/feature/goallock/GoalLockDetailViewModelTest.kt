package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockRuntimeStatus
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalLockDetailViewModelTest {
    private val today = LocalDate.of(2026, 7, 10)

    @Test
    fun loadActiveAndPendingGoalsExposeActions() = runBlocking {
        val active = detailViewModel(DetailDao(goalLock()), DetailAnalytics())
        active.loadGoalLock(today)
        awaitUntil { active.container.stateFlow.value.goalLock != null }
        assertEquals(GoalLockRuntimeStatus.Active, active.container.stateFlow.value.presentation?.runtimeStatus)
        assertTrue(active.container.stateFlow.value.presentation?.canEdit == true)

        val pending = detailViewModel(
            DetailDao(goalLock(startDate = today.plusDays(2), endDate = today.plusDays(8))),
            DetailAnalytics(),
        )
        pending.loadGoalLock(today)
        awaitUntil { pending.container.stateFlow.value.goalLock != null }
        assertEquals(GoalLockRuntimeStatus.Pending, pending.container.stateFlow.value.presentation?.runtimeStatus)
        assertTrue(pending.container.stateFlow.value.presentation?.canEnd == true)
    }

    @Test
    fun terminalGoalsAreReadOnlyAndScreenIsLogged() = runBlocking {
        val analytics = DetailAnalytics()
        val viewModel = detailViewModel(
            DetailDao(goalLock(status = GoalLockStoredStatus.EndedEarly)),
            analytics,
        )

        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        assertFalse(viewModel.container.stateFlow.value.presentation?.canEdit == true)
        assertFalse(viewModel.container.stateFlow.value.presentation?.canEnd == true)
        assertEquals(listOf(KeepAnalyticsScreen.GOAL_LOCK_DETAIL), analytics.screenViews)
    }

    @Test
    fun missingGoalEmitsNotFound() = runBlocking {
        val viewModel = detailViewModel(DetailDao(null), DetailAnalytics())
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.loadGoalLock(today)

        assertEquals(GoalLockDetailSideEffect.NotFound, effect.await())
    }

    @Test
    fun expiredGoalIsCompletedAndPersistenceFailureSuppressesActions() = runBlocking {
        val dao = DetailDao(goalLock(endDate = today.minusDays(1))).apply {
            failConditionalUpdate = true
        }
        val viewModel = detailViewModel(dao, DetailAnalytics())

        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        assertEquals(GoalLockDetailError.Load, viewModel.container.stateFlow.value.error)
        assertEquals(GoalLockRuntimeStatus.Completed, viewModel.container.stateFlow.value.presentation?.runtimeStatus)
        assertFalse(viewModel.container.stateFlow.value.presentation?.canEdit == true)
        assertFalse(viewModel.container.stateFlow.value.presentation?.canEnd == true)
    }

    @Test
    fun refreshAfterEditReloadsLatestGoal() = runBlocking {
        val dao = DetailDao(goalLock())
        val viewModel = detailViewModel(dao, DetailAnalytics())
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        dao.existing = GoalLockEntity.fromDomain(goalLock(goalName = "수정된 목표"))

        viewModel.refreshAfterEdit(today)
        awaitUntil { viewModel.container.stateFlow.value.goalName == "수정된 목표" }

        assertEquals("수정된 목표", viewModel.container.stateFlow.value.goalName)
    }

    @Test
    fun failedRefreshKeepsDataButSuppressesActions() = runBlocking {
        val dao = DetailDao(goalLock())
        val viewModel = detailViewModel(dao, DetailAnalytics())
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        dao.fetchFailuresRemaining = 1

        viewModel.refreshAfterEdit(today)
        awaitUntil { viewModel.container.stateFlow.value.error == GoalLockDetailError.Load }

        assertEquals("시험 준비", viewModel.container.stateFlow.value.goalName)
        assertEquals(GoalLockDetailError.Load, viewModel.container.stateFlow.value.error)
        assertFalse(viewModel.container.stateFlow.value.canEdit)
        assertFalse(viewModel.container.stateFlow.value.canEnd)
    }

    @Test
    fun endFailureKeepsGoalActiveWithoutAnalytics() = runBlocking {
        val dao = DetailDao(goalLock()).apply { failConditionalUpdate = true }
        val analytics = DetailAnalytics()
        val viewModel = detailViewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        viewModel.requestEndGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.showEndConfirmation }

        viewModel.confirmEndGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.error == GoalLockDetailError.End }

        assertEquals(GoalLockStoredStatus.Active, viewModel.container.stateFlow.value.goalLock?.status)
        assertEquals(0, analytics.endedCalls)
        assertFalse(viewModel.container.stateFlow.value.canEdit)
        assertFalse(viewModel.container.stateFlow.value.canEnd)
    }

    @Test
    fun goalThatExpiresBeforeEndConfirmationCompletesInstead() = runBlocking {
        val dao = DetailDao(goalLock(endDate = today))
        val analytics = DetailAnalytics()
        val viewModel = detailViewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.canEnd }
        viewModel.requestEndGoalLock()

        viewModel.confirmEndGoalLock(today.plusDays(1))
        awaitUntil {
            viewModel.container.stateFlow.value.goalLock?.status == GoalLockStoredStatus.Completed
        }

        assertEquals(GoalLockStoredStatus.Completed, dao.updates.single().status)
        assertEquals(1, analytics.completedCalls)
        assertEquals(0, analytics.endedCalls)
    }

    @Test
    fun resumeOnNextDateRefreshesRuntimeStatus() = runBlocking {
        val viewModel = detailViewModel(
            DetailDao(goalLock(endDate = today)),
            DetailAnalytics(),
        )
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.canEnd }

        viewModel.refreshForToday(today.plusDays(1))
        awaitUntil {
            viewModel.container.stateFlow.value.presentation?.runtimeStatus ==
                GoalLockRuntimeStatus.Completed
        }

        assertFalse(viewModel.container.stateFlow.value.canEnd)
    }

    @Test
    fun successfulEndPersistsTracksAndEmitsEnded() = runBlocking {
        val dao = DetailDao(goalLock())
        val analytics = DetailAnalytics()
        val viewModel = detailViewModel(dao, analytics)
        viewModel.loadGoalLock(today)
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        viewModel.requestEndGoalLock()
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.confirmEndGoalLock(today)

        assertEquals(GoalLockDetailSideEffect.Ended, effect.await())
        assertEquals(GoalLockStoredStatus.EndedEarly, dao.updates.single().status)
        assertEquals(1, analytics.endedCalls)
        assertNull(viewModel.container.stateFlow.value.error)
    }

    private fun detailViewModel(dao: DetailDao, analytics: DetailAnalytics) =
        GoalLockDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(GOAL_LOCK_ID_ARG to 42L)),
            goalLockRepository = GoalLockRepository(dao),
            analytics = analytics,
        )

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        error("Condition not met")
    }

    private fun goalLock(
        goalName: String = "시험 준비",
        startDate: LocalDate = LocalDate.of(2026, 7, 4),
        endDate: LocalDate = LocalDate.of(2026, 7, 20),
        status: GoalLockStoredStatus = GoalLockStoredStatus.Active,
    ) = GoalLock(
        id = 42L,
        goalName = goalName,
        startDate = startDate,
        endDate = endDate,
        lockMode = GoalLockMode.AllDay,
        selectedPackages = setOf("com.one", "com.two"),
        status = status,
    )
}

private class DetailDao(existing: GoalLock?) : GoalLockDao {
    var existing: GoalLockEntity? = existing?.let(GoalLockEntity::fromDomain)
    var failConditionalUpdate = false
    var fetchFailuresRemaining = 0
    val updates = mutableListOf<GoalLock>()

    override fun fetchAll(): Flow<List<GoalLockEntity>> = emptyFlow()
    override fun fetch(id: Long): GoalLockEntity? {
        if (fetchFailuresRemaining > 0) {
            fetchFailuresRemaining--
            error("fetch failed")
        }
        return existing?.takeIf { it.id == id }
    }
    override fun insert(goalLock: GoalLockEntity): Long = error("not used")
    override fun update(goalLock: GoalLockEntity) {
        existing = goalLock
        updates += goalLock.toDomain()
    }

    override fun updateIfActive(goalLock: GoalLockEntity): Boolean {
        if (failConditionalUpdate) return false
        val current = fetch(goalLock.id) ?: return false
        if (current.status != GoalLockEntity.STATUS_ACTIVE) return false
        update(goalLock)
        return true
    }
}

private class DetailAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    var endedCalls = 0
    var completedCalls = 0

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
    override fun trackGoalLockEndedEarly(lockMode: String, elapsedDaysBucket: String, reason: String) {
        endedCalls++
    }
    override fun trackGoalLockCompleted(lockMode: String, durationDaysBucket: String) {
        completedCalls++
    }
}
