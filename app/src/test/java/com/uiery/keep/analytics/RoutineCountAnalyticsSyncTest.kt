package com.uiery.keep.analytics

import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.toRepeatDaysBinary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class RoutineCountAnalyticsSyncTest {
    @Test
    fun syncFromRepositoryWritesExplicitZeroWhenNoRoutinesExist() = runBlocking {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineRepository = FakeRoutineCountRepository(emptyList()),
            analytics = analytics,
        )

        sync.syncFromRepository()

        assertEquals(
            listOf(KeepAnalyticsUserProperty.ROUTINES_COUNT to "0"),
            analytics.userProperties,
        )
    }

    @Test
    fun syncFromRepositoryWritesActualRoutineCount() = runBlocking {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineRepository = FakeRoutineCountRepository(
                listOf(
                    routineModel(id = 1L),
                    routineModel(id = 2L),
                ),
            ),
            analytics = analytics,
        )

        sync.syncFromRepository()

        assertEquals(
            listOf(KeepAnalyticsUserProperty.ROUTINES_COUNT to "2"),
            analytics.userProperties,
        )
    }

    @Test
    fun syncFromRoutinesWritesUpdatedCountAfterDeletion() {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineRepository = FakeRoutineCountRepository(
                listOf(
                    routineModel(id = 1L),
                    routineModel(id = 2L),
                ),
            ),
            analytics = analytics,
        )

        sync.syncFromRoutines(listOf(routineModel(id = 1L), routineModel(id = 2L)))
        sync.syncFromRoutines(listOf(routineModel(id = 2L)))

        assertEquals(
            listOf(
                KeepAnalyticsUserProperty.ROUTINES_COUNT to "2",
                KeepAnalyticsUserProperty.ROUTINES_COUNT to "1",
            ),
            analytics.userProperties,
        )
    }
}

private class FakeRoutineCountRepository(
    routines: List<RoutineModel>,
) : RoutineRepository {
    private val state = MutableStateFlow(routines)

    override fun fetchAll(): Flow<List<RoutineModel>> = state
    override suspend fun fetchAllOnce(): List<RoutineModel> = state.value
}

private class RecordingRoutineCountAnalytics : KeepAnalytics {
    val userProperties = mutableListOf<Pair<String, String>>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserProperty(name: String, value: String) {
        userProperties += name to value
    }
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) = Unit
    override fun trackOnboardingStepComplete(stepName: String) = Unit
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

private fun routineModel(id: Long): RoutineModel = RoutineModel(
    id = id,
    name = "Routine $id",
    startTime = LocalTime(hour = 8, minute = 0),
    endTime = LocalTime(hour = 9, minute = 0),
    repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
    lockApplications = listOf("com.example.blocked"),
    isEnabled = true,
    changeLockHours = 0,
)
