package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.AnalyticsGoalLockElapsedDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockEndedEarlyReason
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class GoalLockDetailViewModelTest {
    @Test
    fun loadGoalLockExposesDetailStateAndKeepsConfirmationHidden() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = scheduledGoalLockEntity())
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = DetailRecordingKeepAnalytics())

        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        val state = viewModel.container.stateFlow.value
        assertEquals("SNS 줄이기", state.goalLock?.goalName)
        assertEquals("특정 시간 잠금", state.lockModeLabel)
        assertEquals(3, state.selectedAppCount)
        assertFalse(state.showEndConfirmation)
        assertFalse(state.isEnded)
    }

    @Test
    fun requestAndCancelEndOnlyTogglesConfirmation() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = DetailRecordingKeepAnalytics())
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestEndGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.showEndConfirmation }
        viewModel.cancelEndGoalLock()
        awaitUntil { !viewModel.container.stateFlow.value.showEndConfirmation }

        assertEquals(null, dao.updatedEntity)
    }

    @Test
    fun confirmEndMarksGoalLockEndedEarlyAndTracksBucketedAnalytics() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        viewModel.requestEndGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.showEndConfirmation }

        viewModel.confirmEndGoalLock(today = LocalDate.of(2026, 6, 9))
        awaitUntil { viewModel.container.stateFlow.value.isEnded }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(GoalLockStoredStatus.EndedEarly, updated.status)
        assertFalse(viewModel.container.stateFlow.value.showEndConfirmation)
        assertEquals(
            listOf(
                GoalLockEndedEarlyCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    elapsedDaysBucket = AnalyticsGoalLockElapsedDaysBucket.THREE_TO_SIX,
                    reason = AnalyticsGoalLockEndedEarlyReason.USER_CONFIRMED,
                ),
            ),
            analytics.goalLockEndedEarlyCalls,
        )
    }

    @Test
    fun loadExpiredActiveGoalLockMarksCompletedAndTracksCompletionOnce() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = expiredActiveGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)

        viewModel.loadGoalLock(today = LocalDate.of(2026, 6, 10))
        awaitUntil { viewModel.container.stateFlow.value.isCompleted }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(GoalLockStoredStatus.Completed, updated.status)
        assertFalse(viewModel.container.stateFlow.value.showEndConfirmation)
        assertTrue(viewModel.container.stateFlow.value.isCompleted)
        assertEquals(
            listOf(
                GoalLockCompletedCall(
                    lockMode = AnalyticsGoalLockMode.SCHEDULED,
                    durationDaysBucket = "7",
                ),
            ),
            analytics.goalLockCompletedCalls,
        )
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(20) {
            if (predicate()) return
            delay(10)
        }
    }
}

private fun goalLockDetailViewModel(
    goalLockDao: GoalLockDao,
    analytics: KeepAnalytics,
) = GoalLockDetailViewModel(
    savedStateHandle = SavedStateHandle(mapOf(GOAL_LOCK_ID_ARG to 42L)),
    goalLockDao = goalLockDao,
    analytics = analytics,
)

private fun allDayGoalLockEntity() =
    GoalLockEntity.fromDomain(
        GoalLock(
            id = 42L,
            goalName = "시험 준비",
            startDate = LocalDate.of(2026, 6, 4),
            endDate = LocalDate.of(2026, 7, 3),
            lockMode = GoalLockMode.AllDay,
            selectedPackages = setOf("com.video.app", "com.social.app"),
            status = GoalLockStoredStatus.Active,
        ),
    )

private fun scheduledGoalLockEntity() =
    GoalLockEntity.fromDomain(
        GoalLock(
            id = 42L,
            goalName = "SNS 줄이기",
            startDate = LocalDate.of(2026, 6, 4),
            endDate = LocalDate.of(2026, 6, 30),
            lockMode = GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(23, 0),
            ),
            selectedPackages = setOf("com.video.app", "com.social.app", "com.game.app"),
            status = GoalLockStoredStatus.Active,
        ),
    )

private fun expiredActiveGoalLockEntity() =
    GoalLockEntity.fromDomain(
        GoalLock(
            id = 42L,
            goalName = "프로젝트 마감",
            startDate = LocalDate.of(2026, 6, 2),
            endDate = LocalDate.of(2026, 6, 8),
            lockMode = GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(23, 0),
            ),
            selectedPackages = setOf("com.video.app", "com.social.app"),
            status = GoalLockStoredStatus.Active,
        ),
    )

private class DetailRecordingGoalLockDao(
    private val existing: GoalLockEntity?,
) : GoalLockDao {
    var updatedEntity: GoalLockEntity? = null

    override fun fetchAll(): Flow<List<GoalLockEntity>> = emptyFlow()

    override fun fetch(id: Long): GoalLockEntity? = existing?.takeIf { it.id == id }

    override fun insert(goalLock: GoalLockEntity): Long = error("insert should not be called")

    override fun update(goalLock: GoalLockEntity) {
        updatedEntity = goalLock
    }
}

private data class GoalLockEndedEarlyCall(
    val lockMode: String,
    val elapsedDaysBucket: String,
    val reason: String,
)

private data class GoalLockCompletedCall(
    val lockMode: String,
    val durationDaysBucket: String,
)

private class DetailRecordingKeepAnalytics : KeepAnalytics {
    val goalLockEndedEarlyCalls = mutableListOf<GoalLockEndedEarlyCall>()
    val goalLockCompletedCalls = mutableListOf<GoalLockCompletedCall>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = Unit

    override fun logScreenView(screenName: String) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit

    override fun trackFirstOpen() = Unit

    override fun trackOnboardingStepView(stepName: String) = Unit

    override fun trackOnboardingStepComplete(stepName: String) = Unit

    override fun trackPermissionOutcome(
        permissionName: String,
        outcome: String,
        stepName: String?,
    ) = Unit

    override fun trackFirstLockConfigured(
        source: String,
        selectedAppCount: Int?,
    ) = Unit

    override fun trackLockSessionStart(
        source: String,
        isRoutine: Boolean?,
    ) = Unit

    override fun trackLockSessionEnd(
        source: String,
        endReason: String,
        isRoutine: Boolean?,
    ) = Unit

    override fun trackEmergencyUnlockUsed(
        source: String,
        unlockCountRemaining: Int?,
    ) = Unit

    override fun trackGoalLockEndedEarly(
        lockMode: String,
        elapsedDaysBucket: String,
        reason: String,
    ) {
        goalLockEndedEarlyCalls += GoalLockEndedEarlyCall(
            lockMode = lockMode,
            elapsedDaysBucket = elapsedDaysBucket,
            reason = reason,
        )
    }

    override fun trackGoalLockCompleted(
        lockMode: String,
        durationDaysBucket: String,
    ) {
        goalLockCompletedCalls += GoalLockCompletedCall(
            lockMode = lockMode,
            durationDaysBucket = durationDaysBucket,
        )
    }
}
