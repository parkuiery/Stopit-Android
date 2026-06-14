package com.uiery.keep.analytics

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.database.mapper.toEntity
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
    fun syncFromRoomWritesExplicitZeroWhenNoRoutinesExist() = runBlocking {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineDao = FakeRoutineCountDao(emptyList()),
            analytics = analytics,
        )

        sync.syncFromRoom()

        assertEquals(
            listOf(KeepAnalyticsUserProperty.ROUTINES_COUNT to "0"),
            analytics.userProperties,
        )
    }

    @Test
    fun syncFromRoomWritesActualRoutineCount() = runBlocking {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineDao = FakeRoutineCountDao(
                listOf(
                    routineEntity(id = 1L),
                    routineEntity(id = 2L),
                ),
            ),
            analytics = analytics,
        )

        sync.syncFromRoom()

        assertEquals(
            listOf(KeepAnalyticsUserProperty.ROUTINES_COUNT to "2"),
            analytics.userProperties,
        )
    }

    @Test
    fun syncFromRoutinesWritesUpdatedCountAfterDeletion() {
        val analytics = RecordingRoutineCountAnalytics()
        val sync = RoutineCountAnalyticsSync(
            routineDao = FakeRoutineCountDao(
                listOf(
                    routineEntity(id = 1L),
                    routineEntity(id = 2L),
                ),
            ),
            analytics = analytics,
        )

        sync.syncFromRoutines(listOf(routineEntity(id = 1L), routineEntity(id = 2L)))
        sync.syncFromRoutines(listOf(routineEntity(id = 2L)))

        assertEquals(
            listOf(
                KeepAnalyticsUserProperty.ROUTINES_COUNT to "2",
                KeepAnalyticsUserProperty.ROUTINES_COUNT to "1",
            ),
            analytics.userProperties,
        )
    }
}

private class FakeRoutineCountDao(
    routines: List<RoutineEntity>,
) : RoutineDao {
    private val state = MutableStateFlow(routines)

    override fun fetchAll(): Flow<List<RoutineEntity>> = state
    override fun fetchAllOnce(): List<RoutineEntity> = state.value
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long {
        state.value = state.value + routineEntity
        return routineEntity.id
    }
    override fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
    override fun update(routineEntity: RoutineEntity) {
        state.value = state.value.map { if (it.id == routineEntity.id) routineEntity else it }
    }
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isEnabled = isEnabled) else it }
    }
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

private fun routineEntity(id: Long): RoutineEntity = RoutineModel(
    id = id,
    name = "Routine $id",
    startTime = LocalTime(hour = 8, minute = 0),
    endTime = LocalTime(hour = 9, minute = 0),
    repeatDays = listOf(DayOfWeek.MONDAY).toRepeatDaysBinary(),
    lockApplications = listOf("com.example.blocked"),
    isEnabled = true,
    changeLockHours = 0,
).toEntity()
