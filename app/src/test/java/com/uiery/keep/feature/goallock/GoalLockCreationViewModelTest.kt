package com.uiery.keep.feature.goallock

import com.uiery.keep.analytics.AnalyticsGoalLockDurationSelectionType
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.AnalyticsGoalLockNameType
import com.uiery.keep.analytics.AnalyticsSelectedAppCountBucket
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class GoalLockCreationViewModelTest {
    @Test
    fun createAllDayGoalLockPersistsActiveGoalAndTracksBucketedAnalytics() = runBlocking {
        val dao = RecordingGoalLockDao(insertedId = 17L)
        val analytics = RecordingKeepAnalytics()
        val viewModel = GoalLockCreationViewModel(goalLockDao = dao, analytics = analytics)

        viewModel.setGoalName("시험 준비")
        viewModel.setDateRange(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 7, 3))
        viewModel.setAllDayMode()
        viewModel.setSelectedApps(setOf("com.video.app", "com.social.app", "com.game.app"))
        awaitUntil { viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.createGoalLock(
            durationSelectionType = AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
            goalNameType = AnalyticsGoalLockNameType.PRESET_EXAM,
        )
        awaitUntil { dao.insertedEntity != null }

        val inserted = requireNotNull(dao.insertedEntity).toDomain()
        assertEquals("시험 준비", inserted.goalName)
        assertEquals(LocalDate.of(2026, 6, 4), inserted.startDate)
        assertEquals(LocalDate.of(2026, 7, 3), inserted.endDate)
        assertEquals(GoalLockMode.AllDay, inserted.lockMode)
        assertEquals(setOf("com.video.app", "com.social.app", "com.game.app"), inserted.selectedPackages)
        assertEquals(GoalLockStoredStatus.Active, inserted.status)
        assertEquals(
            listOf(
                GoalLockCreatedCall(
                    durationSelectionType = AnalyticsGoalLockDurationSelectionType.PRESET_DAYS,
                    lockMode = AnalyticsGoalLockMode.ALL_DAY,
                    selectedAppCountBucket = AnalyticsSelectedAppCountBucket.TWO_TO_THREE,
                    goalNameType = AnalyticsGoalLockNameType.PRESET_EXAM,
                ),
            ),
            analytics.goalLockCreatedCalls,
        )
        assertEquals(GoalLockCreationSideEffect.Created(17L), viewModel.container.sideEffectFlow.first())
    }

    @Test
    fun createScheduledGoalLockPersistsScheduleAndSevenPlusAppBucket() = runBlocking {
        val dao = RecordingGoalLockDao(insertedId = 18L)
        val analytics = RecordingKeepAnalytics()
        val viewModel = GoalLockCreationViewModel(goalLockDao = dao, analytics = analytics)
        val selectedApps = (1..7).map { "com.example.app$it" }.toSet()

        viewModel.setGoalName("SNS 줄이기")
        viewModel.setDateRange(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 30))
        viewModel.setScheduledMode(
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(23, 0),
        )
        viewModel.setSelectedApps(selectedApps)
        awaitUntil { viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.createGoalLock(
            durationSelectionType = AnalyticsGoalLockDurationSelectionType.END_DATE,
            goalNameType = AnalyticsGoalLockNameType.CUSTOM,
        )
        awaitUntil { dao.insertedEntity != null }

        val inserted = requireNotNull(dao.insertedEntity).toDomain()
        val mode = inserted.lockMode as GoalLockMode.Scheduled
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), mode.repeatDays)
        assertEquals(LocalTime.of(19, 0), mode.startTime)
        assertEquals(LocalTime.of(23, 0), mode.endTime)
        assertEquals(selectedApps, inserted.selectedPackages)
        assertEquals(AnalyticsGoalLockMode.SCHEDULED, analytics.goalLockCreatedCalls.single().lockMode)
        assertEquals(AnalyticsSelectedAppCountBucket.SEVEN_PLUS, analytics.goalLockCreatedCalls.single().selectedAppCountBucket)
    }

    @Test
    fun invalidGoalLockDoesNotPersistOrTrack() = runBlocking {
        val dao = RecordingGoalLockDao()
        val analytics = RecordingKeepAnalytics()
        val viewModel = GoalLockCreationViewModel(goalLockDao = dao, analytics = analytics)

        viewModel.setGoalName("시험 준비")
        viewModel.setDateRange(LocalDate.of(2026, 7, 3), LocalDate.of(2026, 6, 4))
        viewModel.setSelectedApps(setOf("com.video.app"))
        awaitUntil { !viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.createGoalLock(
            durationSelectionType = AnalyticsGoalLockDurationSelectionType.CUSTOM_DAYS,
            goalNameType = AnalyticsGoalLockNameType.CUSTOM,
        )
        delay(50)

        assertFalse(viewModel.container.stateFlow.value.isCreateEnabled)
        assertEquals(null, dao.insertedEntity)
        assertTrue(analytics.goalLockCreatedCalls.isEmpty())
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(20) {
            if (predicate()) return
            delay(10)
        }
    }
}

private class RecordingGoalLockDao(
    private val insertedId: Long = 1L,
) : GoalLockDao {
    var insertedEntity: GoalLockEntity? = null

    override fun fetchAll(): Flow<List<GoalLockEntity>> = emptyFlow()

    override fun fetch(id: Long): GoalLockEntity? = null

    override fun insert(goalLock: GoalLockEntity): Long {
        insertedEntity = goalLock
        return insertedId
    }

    override fun update(goalLock: GoalLockEntity) = Unit
}

private data class GoalLockCreatedCall(
    val durationSelectionType: String,
    val lockMode: String,
    val selectedAppCountBucket: String,
    val goalNameType: String,
)

private class RecordingKeepAnalytics : KeepAnalytics {
    val goalLockCreatedCalls = mutableListOf<GoalLockCreatedCall>()

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

    override fun trackGoalLockCreated(
        durationSelectionType: String,
        lockMode: String,
        selectedAppCountBucket: String,
        goalNameType: String,
    ) {
        goalLockCreatedCalls += GoalLockCreatedCall(
            durationSelectionType = durationSelectionType,
            lockMode = lockMode,
            selectedAppCountBucket = selectedAppCountBucket,
            goalNameType = goalNameType,
        )
    }
}
