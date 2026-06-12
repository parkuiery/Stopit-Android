package com.uiery.keep.feature.goallock

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.analytics.AnalyticsGoalLockChangedField
import com.uiery.keep.analytics.AnalyticsGoalLockDurationDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockElapsedDaysBucket
import com.uiery.keep.analytics.AnalyticsGoalLockEndedEarlyReason
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.data.goallock.GoalLockRepository
import com.uiery.keep.domain.goallock.GoalLock
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
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
        assertEquals(
            GoalLockMode.Scheduled(
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(23, 0),
            ),
            state.goalLock?.lockMode,
        )
        assertEquals(3, state.selectedAppCount)
        assertFalse(state.showEndConfirmation)
        assertFalse(state.isEnded)
    }

    @Test
    fun detailFlowLogsCanonicalGoalLockDetailScreenViewOnCreation() {
        val analytics = DetailRecordingKeepAnalytics()

        goalLockDetailViewModel(goalLockDao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity()), analytics = analytics)

        assertEquals(
            listOf(KeepAnalyticsScreen.GOAL_LOCK_DETAIL),
            analytics.screenViews,
        )
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

    @Test
    fun confirmSelectedAppsUpdateReplacesPackagesAndTracksGoalLockUpdated() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateSelectedApps(setOf(" com.new.video ", "com.new.video", "com.new.game"))
        awaitUntil { viewModel.container.stateFlow.value.showUpdateAppsConfirmation }
        viewModel.confirmUpdateSelectedApps()
        awaitUntil { viewModel.container.stateFlow.value.goalLock?.selectedPackages == setOf("com.new.video", "com.new.game") }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(42L, updated.id)
        assertEquals("시험 준비", updated.goalName)
        assertEquals(LocalDate.of(2026, 6, 4), updated.startDate)
        assertEquals(LocalDate.of(2026, 7, 3), updated.endDate)
        assertEquals(GoalLockMode.AllDay, updated.lockMode)
        assertEquals(GoalLockStoredStatus.Active, updated.status)
        assertEquals(setOf("com.new.video", "com.new.game"), updated.selectedPackages)
        assertFalse(viewModel.container.stateFlow.value.showUpdateAppsConfirmation)
        assertEquals(
            listOf(
                GoalLockUpdatedCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    changedField = AnalyticsGoalLockChangedField.APPS,
                ),
            ),
            analytics.goalLockUpdatedCalls,
        )
    }

    @Test
    fun confirmSelectedAppsUpdateRejectsEmptySelectionWithoutAnalytics() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateSelectedApps(setOf(" ", "\t"))
        viewModel.confirmUpdateSelectedApps()
        delay(50)

        assertEquals(null, dao.updatedEntity)
        assertFalse(viewModel.container.stateFlow.value.showUpdateAppsConfirmation)
        assertEquals(emptyList<GoalLockUpdatedCall>(), analytics.goalLockUpdatedCalls)
    }

    @Test
    fun confirmGoalNameUpdateTrimsNameAndTracksGoalLockUpdated() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateGoalName("  30일 SNS 디톡스  ")
        awaitUntil { viewModel.container.stateFlow.value.showUpdateGoalNameConfirmation }
        viewModel.confirmUpdateGoalName()
        awaitUntil { viewModel.container.stateFlow.value.goalLock?.goalName == "30일 SNS 디톡스" }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals("30일 SNS 디톡스", updated.goalName)
        assertEquals(LocalDate.of(2026, 6, 4), updated.startDate)
        assertEquals(LocalDate.of(2026, 7, 3), updated.endDate)
        assertEquals(GoalLockMode.AllDay, updated.lockMode)
        assertEquals(setOf("com.video.app", "com.social.app"), updated.selectedPackages)
        assertFalse(viewModel.container.stateFlow.value.showUpdateGoalNameConfirmation)
        assertEquals(
            listOf(
                GoalLockUpdatedCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    changedField = AnalyticsGoalLockChangedField.NAME,
                ),
            ),
            analytics.goalLockUpdatedCalls,
        )
    }

    @Test
    fun confirmGoalNameUpdateRejectsBlankOrSameNameWithoutAnalytics() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateGoalName("  ")
        viewModel.confirmUpdateGoalName()
        viewModel.requestUpdateGoalName(" 시험 준비 ")
        viewModel.confirmUpdateGoalName()
        delay(50)

        assertEquals(null, dao.updatedEntity)
        assertFalse(viewModel.container.stateFlow.value.showUpdateGoalNameConfirmation)
        assertEquals(emptyList<GoalLockUpdatedCall>(), analytics.goalLockUpdatedCalls)
    }

    @Test
    fun confirmDurationUpdateRecalculatesEndDateAndTracksDurationChanged() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateDurationDays(14)
        awaitUntil { viewModel.container.stateFlow.value.showUpdateDurationConfirmation }
        viewModel.confirmUpdateDuration()
        awaitUntil { viewModel.container.stateFlow.value.goalLock?.endDate == LocalDate.of(2026, 6, 17) }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(LocalDate.of(2026, 6, 4), updated.startDate)
        assertEquals(LocalDate.of(2026, 6, 17), updated.endDate)
        assertEquals(GoalLockMode.AllDay, updated.lockMode)
        assertEquals(setOf("com.video.app", "com.social.app"), updated.selectedPackages)
        assertFalse(viewModel.container.stateFlow.value.showUpdateDurationConfirmation)
        assertEquals(
            listOf(
                GoalLockUpdatedCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    changedField = AnalyticsGoalLockChangedField.DURATION,
                ),
            ),
            analytics.goalLockUpdatedCalls,
        )
    }

    @Test
    fun confirmDurationUpdateCompletesGoalLockWhenNewEndDateIsPast() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock(today = LocalDate.of(2026, 6, 10))
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }

        viewModel.requestUpdateDurationDays(5)
        awaitUntil { viewModel.container.stateFlow.value.showUpdateDurationConfirmation }
        viewModel.confirmUpdateDuration(today = LocalDate.of(2026, 6, 10))
        awaitUntil { viewModel.container.stateFlow.value.isCompleted }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(LocalDate.of(2026, 6, 8), updated.endDate)
        assertEquals(GoalLockStoredStatus.Completed, updated.status)
        assertTrue(viewModel.container.stateFlow.value.isCompleted)
        assertFalse(viewModel.container.stateFlow.value.isEnded)
        assertFalse(viewModel.container.stateFlow.value.showUpdateDurationConfirmation)
        assertEquals(
            listOf(
                GoalLockUpdatedCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    changedField = AnalyticsGoalLockChangedField.DURATION,
                ),
            ),
            analytics.goalLockUpdatedCalls,
        )
        assertEquals(
            listOf(
                GoalLockCompletedCall(
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    durationDaysBucket = AnalyticsGoalLockDurationDaysBucket.ONE_TO_SIX,
                ),
            ),
            analytics.goalLockCompletedCalls,
        )
    }

    @Test
    fun confirmLockModeUpdateStoresScheduledModeAndTracksLockModeChanged() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        val scheduled = GoalLockMode.Scheduled(
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(23, 0),
        )

        viewModel.requestUpdateLockMode(scheduled)
        awaitUntil { viewModel.container.stateFlow.value.showUpdateLockModeConfirmation }
        viewModel.confirmUpdateLockMode()
        awaitUntil { viewModel.container.stateFlow.value.goalLock?.lockMode == scheduled }

        val updated = requireNotNull(dao.updatedEntity).toDomain()
        assertEquals(scheduled, updated.lockMode)
        assertEquals(LocalDate.of(2026, 6, 4), updated.startDate)
        assertEquals(LocalDate.of(2026, 7, 3), updated.endDate)
        assertEquals(setOf("com.video.app", "com.social.app"), updated.selectedPackages)
        assertFalse(viewModel.container.stateFlow.value.showUpdateLockModeConfirmation)
        assertEquals(
            listOf(
                GoalLockUpdatedCall(
                    lockMode = AnalyticsGoalLockMode.SCHEDULED,
                    changedField = AnalyticsGoalLockChangedField.LOCK_MODE,
                ),
            ),
            analytics.goalLockUpdatedCalls,
        )
    }

    @Test
    fun sameStartEndScheduledModeUpdateIsRejectedWithoutAnalytics() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = allDayGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock()
        awaitUntil { viewModel.container.stateFlow.value.goalLock != null }
        val invalidScheduled = GoalLockMode.Scheduled(
            repeatDays = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(19, 0),
        )

        viewModel.requestUpdateLockMode(invalidScheduled)
        viewModel.confirmUpdateLockMode()
        delay(50)

        assertEquals(null, dao.updatedEntity)
        assertFalse(viewModel.container.stateFlow.value.showUpdateLockModeConfirmation)
        assertEquals(GoalLockMode.AllDay, viewModel.container.stateFlow.value.goalLock?.lockMode)
        assertEquals(emptyList<GoalLockUpdatedCall>(), analytics.goalLockUpdatedCalls)
    }

    @Test
    fun updateDurationAndLockModeRejectCompletedGoalLockWithoutAnalytics() = runBlocking {
        val dao = DetailRecordingGoalLockDao(existing = expiredActiveGoalLockEntity())
        val analytics = DetailRecordingKeepAnalytics()
        val viewModel = goalLockDetailViewModel(goalLockDao = dao, analytics = analytics)
        viewModel.loadGoalLock(today = LocalDate.of(2026, 6, 10))
        awaitUntil { viewModel.container.stateFlow.value.isCompleted }
        dao.updatedEntity = null
        analytics.goalLockCompletedCalls.clear()

        viewModel.requestUpdateDurationDays(30)
        viewModel.confirmUpdateDuration()
        viewModel.requestUpdateLockMode(GoalLockMode.AllDay)
        viewModel.confirmUpdateLockMode()
        delay(50)

        assertEquals(null, dao.updatedEntity)
        assertFalse(viewModel.container.stateFlow.value.showUpdateDurationConfirmation)
        assertFalse(viewModel.container.stateFlow.value.showUpdateLockModeConfirmation)
        assertEquals(emptyList<GoalLockUpdatedCall>(), analytics.goalLockUpdatedCalls)
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
    goalLockRepository = GoalLockRepository(goalLockDao),
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

private data class GoalLockUpdatedCall(
    val lockMode: String,
    val changedField: String,
)

private class DetailRecordingKeepAnalytics : KeepAnalytics {
    val screenViews = mutableListOf<String>()
    val goalLockEndedEarlyCalls = mutableListOf<GoalLockEndedEarlyCall>()
    val goalLockCompletedCalls = mutableListOf<GoalLockCompletedCall>()
    val goalLockUpdatedCalls = mutableListOf<GoalLockUpdatedCall>()

    override fun logEvent(
        name: String,
        params: Map<String, Any?>,
    ) = Unit

    override fun logScreenView(screenName: String) {
        screenViews += screenName
    }

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

    override fun trackGoalLockUpdated(
        lockMode: String,
        changedField: String,
    ) {
        goalLockUpdatedCalls += GoalLockUpdatedCall(
            lockMode = lockMode,
            changedField = changedField,
        )
    }
}
