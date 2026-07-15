package com.uiery.keep.feature.home

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.data.firstpromise.FirstPromiseCreationResult
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceCoordinator
import com.uiery.keep.data.firstpromise.FirstPromisePersistenceResult
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.feature.review.FakeDataStore
import com.uiery.keep.model.RoutineModel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelFirstPromiseResumeTest {
    @Test
    fun unavailablePermissionOpensLauncherThenResumeFinalizesSameRoutine() = runBlocking {
        val fixture = fixture(canSchedule = false)

        assertTrue(fixture.recovery.load() is FirstPromiseResumeDecision.Show)
        assertEquals(FirstPromiseResumeDecision.OpenSettings, fixture.recovery.activate())
        fixture.canSchedule = true
        assertEquals(FirstPromiseResumeDecision.Hidden, fixture.recovery.onResume())

        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
        assertEquals(42L, fixture.store.readState().routineId)
    }

    @Test
    fun availablePermissionFinalizesDirectlyWithoutLauncher() = runBlocking {
        val fixture = fixture(canSchedule = true)

        assertEquals(FirstPromiseResumeDecision.Hidden, fixture.recovery.load())

        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
        assertFalse(fixture.recovery.settingsLaunchPending)
    }

    @Test
    fun transientFailureKeepsVisibleRetry() = runBlocking {
        val fixture = fixture(canSchedule = true, finalizeFails = true)

        val result = fixture.recovery.load()

        assertTrue(result is FirstPromiseResumeDecision.Show)
        assertEquals(listOf(42L), fixture.coordinator.finalizedIds)
    }

    @Test
    fun deletedMappingAndSuccessfulActivationHideCard() = runBlocking {
        val deleted = fixture(canSchedule = false, mappingExists = false)
        assertEquals(FirstPromiseResumeDecision.Hidden, deleted.recovery.load())

        val success = fixture(canSchedule = true)
        assertEquals(FirstPromiseResumeDecision.Hidden, success.recovery.load())
        assertEquals(FirstPromiseScheduleState.Enabled, success.store.readState().scheduleState)
    }
}

private data class HomeResumeFixture(
    val recovery: FirstPromiseHomeRecoveryCoordinator,
    val coordinator: FakeHomeRecoveryPersistence,
    val store: FirstPromiseDraftStore,
    var canSchedule: Boolean,
)

private fun fixture(
    canSchedule: Boolean,
    finalizeFails: Boolean = false,
    mappingExists: Boolean = true,
): HomeResumeFixture {
    val store = FirstPromiseDraftStore(
        FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
                    FirstPromiseOnboardingState(
                        phase = FirstPromisePhase.CompletedDisabled,
                        routineId = 42L,
                        scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
                    ),
                ),
            ),
        ),
    )
    val persistence = FakeHomeRecoveryPersistence(store, finalizeFails, mappingExists)
    lateinit var fixture: HomeResumeFixture
    val recovery = FirstPromiseHomeRecoveryCoordinator(
        draftStore = store,
        coordinator = persistence,
        canScheduleExactAlarms = { fixture.canSchedule },
    )
    return HomeResumeFixture(recovery, persistence, store, canSchedule).also { fixture = it }
}

private class FakeHomeRecoveryPersistence(
    private val store: FirstPromiseDraftStore,
    private val finalizeFails: Boolean,
    private val mappingExists: Boolean,
) : FirstPromisePersistenceCoordinator {
    val finalizedIds = mutableListOf<Long>()

    override suspend fun persistCurrentDraft() = FirstPromisePersistenceResult.MissingDraft

    override suspend fun readCurrentMapping(): FirstPromiseCreationResult? =
        if (mappingExists) creation(enabled = false) else null

    override suspend fun reconcileExistingRoutine(routineId: Long): FirstPromisePersistenceResult =
        if (mappingExists) FirstPromisePersistenceResult.Succeeded(creation(false))
        else FirstPromisePersistenceResult.MissingRoutine

    override suspend fun finalizeExistingRoutine(routineId: Long): FirstPromisePersistenceResult {
        finalizedIds += routineId
        if (finalizeFails) return FirstPromisePersistenceResult.Failed(IllegalStateException("schedule"))
        store.resolveScheduleState(routineId, FirstPromiseScheduleState.Enabled)
        return FirstPromisePersistenceResult.Succeeded(creation(true))
    }

    private fun creation(enabled: Boolean) = FirstPromiseCreationResult(
        routineId = 42L,
        routine = RoutineModel(
            id = 42L,
            name = "YouTube",
            startTime = LocalTime(23, 0),
            endTime = LocalTime(23, 30),
            repeatDays = "1010100",
            lockApplications = listOf("video.app"),
            isEnabled = enabled,
        ),
        scheduleState = if (enabled) {
            FirstPromiseScheduleState.Enabled
        } else {
            FirstPromiseScheduleState.DisabledExactAlarmMissing
        },
        schedulingSucceeded = enabled,
        created = false,
        draftId = "draft",
    )
}
