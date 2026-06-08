package com.uiery.keep.feature.goallock

import androidx.datastore.core.DataStore
import com.uiery.keep.analytics.AnalyticsGoalLockDurationSelectionType
import com.uiery.keep.analytics.AnalyticsGoalLockEntrySurface
import com.uiery.keep.analytics.AnalyticsGoalLockMode
import com.uiery.keep.analytics.AnalyticsGoalLockNameType
import com.uiery.keep.analytics.AnalyticsSelectedAppCountBucket
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.database.dao.GoalLockDao
import com.uiery.keep.database.entity.GoalLockEntity
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.goallock.GoalLockMode
import com.uiery.keep.domain.goallock.GoalLockStoredStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun creationFlowEntryTracksMenuSurfaceOnce() {
        val analytics = RecordingKeepAnalytics()

        createViewModel(analytics = analytics)

        assertEquals(
            listOf(AnalyticsGoalLockEntrySurface.MENU),
            analytics.goalLockCreateStartedCalls,
        )
    }

    @Test
    fun createAllDayGoalLockPersistsActiveGoalAndTracksBucketedAnalytics() = runBlocking {
        val dao = RecordingGoalLockDao(insertedId = 17L)
        val analytics = RecordingKeepAnalytics()
        val viewModel = createViewModel(dao = dao, analytics = analytics)

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
        val viewModel = createViewModel(dao = dao, analytics = analytics)
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
        awaitUntil { analytics.goalLockCreatedCalls.isNotEmpty() }

        val inserted = requireNotNull(dao.insertedEntity).toDomain()
        val mode = inserted.lockMode as GoalLockMode.Scheduled
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), mode.repeatDays)
        assertEquals(LocalTime.of(19, 0), mode.startTime)
        assertEquals(LocalTime.of(23, 0), mode.endTime)
        assertEquals(selectedApps, inserted.selectedPackages)
        val createdCall = analytics.goalLockCreatedCalls.single()
        assertEquals(AnalyticsGoalLockMode.SCHEDULED, createdCall.lockMode)
        assertEquals(AnalyticsSelectedAppCountBucket.SEVEN_PLUS, createdCall.selectedAppCountBucket)
    }

    @Test
    fun invalidGoalLockDoesNotPersistOrTrack() = runBlocking {
        val dao = RecordingGoalLockDao()
        val analytics = RecordingKeepAnalytics()
        val viewModel = createViewModel(dao = dao, analytics = analytics)

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

    @Test
    fun scheduledGoalLockWithSameStartAndEndTimeIsDisabledAndDoesNotPersist() = runBlocking {
        val dao = RecordingGoalLockDao()
        val analytics = RecordingKeepAnalytics()
        val viewModel = createViewModel(dao = dao, analytics = analytics)

        viewModel.setGoalName("SNS 줄이기")
        viewModel.setDateRange(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 30))
        viewModel.setSelectedApps(setOf("com.video.app"))
        viewModel.setScheduledMode(
            repeatDays = setOf(DayOfWeek.MONDAY),
            startTime = LocalTime.of(19, 0),
            endTime = LocalTime.of(19, 0),
        )
        awaitUntil { !viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.createGoalLock()
        delay(50)

        assertFalse(viewModel.container.stateFlow.value.isCreateEnabled)
        assertTrue(viewModel.container.stateFlow.value.hasInvalidScheduledTime)
        assertEquals(null, dao.insertedEntity)
        assertTrue(analytics.goalLockCreatedCalls.isEmpty())
    }

    @Test
    fun loadSelectedAppsSeedsGoalLockCreationFromCurrentBlockingSelection() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[PreferencesKey.SELECTED_APP_PACKAGES] = setOf("com.video.app", "com.social.app")
        }
        val viewModel = createViewModel(blockingStateStore = BlockingStateStore(dataStore))

        viewModel.loadSelectedAppsFromCurrentSelection()
        awaitUntil { viewModel.container.stateFlow.value.selectedApps.isNotEmpty() }

        assertEquals(
            setOf("com.video.app", "com.social.app"),
            viewModel.container.stateFlow.value.selectedApps,
        )
    }

    @Test
    fun customDaysAndEndDateSelectionsPersistExpectedRangesAndAnalyticsTypes() = runBlocking {
        val dao = RecordingGoalLockDao(insertedId = 19L)
        val analytics = RecordingKeepAnalytics()
        val viewModel = createViewModel(dao = dao, analytics = analytics)
        val today = LocalDate.of(2026, 6, 4)

        viewModel.setGoalName("프로젝트 마감")
        viewModel.setSelectedApps(setOf("com.video.app"))
        viewModel.setCustomDurationDays(today = today, days = 10)
        awaitUntil { viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.createGoalLock()
        awaitUntil { dao.insertedEntity != null }

        val customDaysGoal = requireNotNull(dao.insertedEntity).toDomain()
        assertEquals(today, customDaysGoal.startDate)
        assertEquals(LocalDate.of(2026, 6, 13), customDaysGoal.endDate)
        assertEquals(AnalyticsGoalLockDurationSelectionType.CUSTOM_DAYS, analytics.goalLockCreatedCalls.single().durationSelectionType)
        assertEquals(AnalyticsGoalLockNameType.CUSTOM, analytics.goalLockCreatedCalls.single().goalNameType)

        dao.insertedEntity = null
        analytics.goalLockCreatedCalls.clear()
        viewModel.setEndDateSelection(today = today, endDate = LocalDate.of(2026, 7, 1))
        awaitUntil { viewModel.container.stateFlow.value.endDate == LocalDate.of(2026, 7, 1) }

        viewModel.createGoalLock()
        awaitUntil { dao.insertedEntity != null }

        val endDateGoal = requireNotNull(dao.insertedEntity).toDomain()
        assertEquals(today, endDateGoal.startDate)
        assertEquals(LocalDate.of(2026, 7, 1), endDateGoal.endDate)
        assertEquals(AnalyticsGoalLockDurationSelectionType.END_DATE, analytics.goalLockCreatedCalls.single().durationSelectionType)
    }

    @Test
    fun pickerSelectionReplacesSeededAppsAndEmptySelectionDisablesCreation() = runBlocking {
        val dao = RecordingGoalLockDao(insertedId = 20L)
        val viewModel = createViewModel(dao = dao)

        viewModel.setGoalName("SNS 줄이기")
        viewModel.setDateRange(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 10))
        viewModel.setSelectedApps(setOf("com.video.app", "com.social.app"))
        awaitUntil { viewModel.container.stateFlow.value.isCreateEnabled }

        viewModel.setSelectedApps(setOf("  com.social.app  ", "com.social.app", "  com.focus.app  "))
        awaitUntil { viewModel.container.stateFlow.value.selectedApps == setOf("com.social.app", "com.focus.app") }

        viewModel.setSelectedApps(emptySet())
        awaitUntil { !viewModel.container.stateFlow.value.isCreateEnabled }
        viewModel.createGoalLock()
        delay(50)
        assertEquals(null, dao.insertedEntity)

        viewModel.setSelectedApps(setOf("com.focus.app"))
        awaitUntil { viewModel.container.stateFlow.value.isCreateEnabled }
        viewModel.createGoalLock()
        awaitUntil { dao.insertedEntity != null }

        assertEquals(setOf("com.focus.app"), requireNotNull(dao.insertedEntity).toDomain().selectedPackages)
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        repeat(20) {
            if (predicate()) return
            delay(10)
        }
    }
}

private fun createViewModel(
    dao: GoalLockDao = RecordingGoalLockDao(),
    analytics: KeepAnalytics = RecordingKeepAnalytics(),
    blockingStateStore: BlockingStateStore = BlockingStateStore(FakeDataStore()),
): GoalLockCreationViewModel =
    GoalLockCreationViewModel(
        goalLockRepository = GoalLockRepository(dao),
        analytics = analytics,
        blockingStateStore = blockingStateStore,
    )

private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }

    companion object {
        fun withPrefs(block: androidx.datastore.preferences.core.MutablePreferences.() -> Unit): FakeDataStore {
            val preferences = mutablePreferencesOf()
            preferences.block()
            return FakeDataStore(preferences)
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
    val goalLockCreateStartedCalls = mutableListOf<String>()
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

    override fun trackGoalLockCreateStarted(entrySurface: String) {
        goalLockCreateStartedCalls += entrySurface
    }

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
