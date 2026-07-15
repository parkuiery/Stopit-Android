package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.domain.firstpromise.FirstPromiseEmergencyResult
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePersistenceResolution
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.FirstPromiseStatePolicy
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.PendingOnboardingAnalyticsEvent
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class FirstPromiseDraftStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val commandMutex = Mutex()

    suspend fun readStateResult(): FirstPromiseStateReadResult =
        decodeResult(dataStore.data.first()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])

    suspend fun readState(): FirstPromiseOnboardingState =
        readStateResult().requireAvailable()

    fun observeStateResult(): Flow<FirstPromiseStateReadResult> =
        dataStore.data
            .map { preferences ->
                decodeResult(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            }
            .distinctUntilChanged()

    suspend fun assignIfAbsent(
        variant: OnboardingVariant,
        version: OnboardingAssignmentVersion,
    ): FirstPromiseOnboardingState = commandMutex.withLock {
        val current = readState()
        if (FirstPromiseStatePolicy.assignIfAbsent(current, variant, version) !is FirstPromiseStateMutation.Changed) {
            current
        } else {
            var result = current
            dataStore.edit { preferences ->
                val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
                result = when (val mutation = FirstPromiseStatePolicy.assignIfAbsent(latest, variant, version)) {
                    is FirstPromiseStateMutation.Changed -> mutation.state.also { preferences.write(it) }
                    else -> latest
                }
            }
            result
        }
    }

    suspend fun advanceToUsageAccess(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::advanceToUsageAccess)

    suspend fun choosePersonalizedGoal(goal: FirstPromiseGoal): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.choosePersonalizedGoal(it, goal) }

    suspend fun chooseManualGoal(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::chooseManualGoal)

    suspend fun completeUsageAccess(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::completeUsageAccess)

    suspend fun chooseManualSetup(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::chooseManualSetup)

    suspend fun beginUsageAnalysis(attemptId: Long): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.beginUsageAnalysis(it, attemptId) }

    suspend fun requestAccessibility(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::requestAccessibility)

    suspend fun returnToDraft(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::returnToDraft)

    suspend fun requestNotification(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::requestNotification)

    suspend fun beginPersistence(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::beginPersistence)

    suspend fun markPersistenceFailed(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::markPersistenceFailed)

    suspend fun completeOnboarding(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::completeOnboarding)

    suspend fun selectGoal(
        goal: FirstPromiseGoal,
        path: FirstPromisePath,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.selectGoal(it, goal, path) }

    suspend fun markMilestone(milestone: FirstPromiseMilestone): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.markMilestone(it, milestone) }

    suspend fun markExposedIfNeeded(variant: OnboardingVariant): Boolean =
        applyMutation { state -> FirstPromiseStatePolicy.markExposure(state, variant) } is
            FirstPromiseStateMutation.Changed

    suspend fun markGoalSelectViewed(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::markGoalSelectViewed)

    suspend fun markUsageAccessViewed(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::markUsageAccessViewed)

    suspend fun markPromiseProposalViewed(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::markPromiseProposalViewed)

    suspend fun markPromiseResultViewed(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::markPromiseResultViewed)

    suspend fun startFirstPromise(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::startFirstPromise)

    suspend fun acknowledgePendingAnalyticsEvent(
        event: PendingOnboardingAnalyticsEvent,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.acknowledgePendingAnalyticsEvent(it, event) }

    suspend fun createManualDraft(
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.createManualDraft(it, draft, reason) }

    suspend fun editDraft(
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.editDraft(it, draft, reason) }

    suspend fun setPendingSystemAction(action: PendingSystemAction): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.setPendingSystemAction(it, action) }

    suspend fun clearPendingSystemAction(): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.setPendingSystemAction(it, null) }

    suspend fun completeAnalysis(
        attemptId: Long,
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): Boolean =
        applyMutation { FirstPromiseStatePolicy.completeAnalysis(it, attemptId, draft, reason) } is
            FirstPromiseStateMutation.Changed

    suspend fun failAnalysis(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.failAnalysis(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun recordPersistenceMapping(
        routineId: Long,
        scheduleState: FirstPromiseScheduleState,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.recordPersistenceMapping(it, routineId, scheduleState) }

    suspend fun resolveScheduleState(
        routineId: Long,
        scheduleState: FirstPromiseScheduleState,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.resolveScheduleState(it, routineId, scheduleState) }

    suspend fun recoverAfterRecreation(): FirstPromiseStateMutation =
        applyMutation(FirstPromiseStatePolicy::recoverAfterRecreation)

    suspend fun applyEmergency(): FirstPromiseEmergencyResult =
        applyEmergencyMutation(FirstPromiseStatePolicy::applyEmergency)

    suspend fun resolveEmergencyPersistence(
        resolution: FirstPromisePersistenceResolution,
    ): FirstPromiseEmergencyResult =
        applyEmergencyMutation { FirstPromiseStatePolicy.resolveEmergencyPersistence(it, resolution) }

    suspend fun beginUsagePermissionAttempt(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.beginUsagePermissionAttempt(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun beginUsagePermissionSettingsAttempt(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.beginUsagePermissionSettingsAttempt(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun beginManualUsagePermissionAttempt(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.beginUsagePermissionAttempt(it, attemptId, manual = true) } is
            FirstPromiseStateMutation.Changed

    suspend fun chooseManualUsageAccess(attemptId: Long): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.chooseManualUsageAccess(it, attemptId) }

    suspend fun recordUsagePermissionOpened(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.markUsagePermissionOpened(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun recordUsagePermissionLaunchFailed(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.markUsagePermissionLaunchFailed(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun recordUsagePermissionResume(
        attemptId: Long,
        permissionGranted: Boolean,
        sameProcess: Boolean,
    ): UsagePermissionOutcome? {
        val mutation = applyMutation {
            FirstPromiseStatePolicy.recordUsagePermissionResume(
                state = it,
                attemptId = attemptId,
                permissionGranted = permissionGranted,
                sameProcess = sameProcess,
            )
        }
        return (mutation as? FirstPromiseStateMutation.Changed)
            ?.state
            ?.usagePermissionAttempt
            ?.terminalOutcome
    }

    suspend fun reconcileUsagePermissionAfterRecreation(
        permissionGranted: Boolean,
    ): UsagePermissionOutcome? {
        val mutation = applyMutation {
            FirstPromiseStatePolicy.reconcileUsagePermissionAfterRecreation(it, permissionGranted)
        }
        return (mutation as? FirstPromiseStateMutation.Changed)
            ?.state
            ?.usagePermissionAttempt
            ?.terminalOutcome
    }

    private suspend fun applyMutation(
        policy: (FirstPromiseOnboardingState) -> FirstPromiseStateMutation,
    ): FirstPromiseStateMutation = commandMutex.withLock {
        val initialMutation = policy(readState())
        if (initialMutation !is FirstPromiseStateMutation.Changed) {
            return@withLock initialMutation
        }

        var result: FirstPromiseStateMutation = initialMutation
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            result = policy(latest)
            (result as? FirstPromiseStateMutation.Changed)?.state?.let { state ->
                preferences.write(state)
            }
        }
        result
    }

    private suspend fun applyEmergencyMutation(
        policy: (FirstPromiseOnboardingState) -> FirstPromiseEmergencyResult,
    ): FirstPromiseEmergencyResult = commandMutex.withLock {
        val current = readState()
        val initialResult = policy(current)
        if (initialResult.state == current) {
            return@withLock initialResult
        }

        var result = initialResult
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            result = policy(latest)
            if (result.state != latest) {
                preferences.write(result.state)
            }
        }
        result
    }

    private fun MutablePreferences.write(state: FirstPromiseOnboardingState) {
        this[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE] = json.encodeToString(state)
    }

    private fun decode(value: String?): FirstPromiseOnboardingState =
        decodeResult(value).requireAvailable()

    private fun decodeResult(value: String?): FirstPromiseStateReadResult = when (value) {
        null -> FirstPromiseStateReadResult.Available(FirstPromiseOnboardingState())
        else -> runCatching { json.decodeFromString<FirstPromiseOnboardingState>(value) }
            .fold(
                onSuccess = FirstPromiseStateReadResult::Available,
                onFailure = { FirstPromiseStateReadResult.Corrupted },
            )
    }
}

sealed interface FirstPromiseStateReadResult {
    data class Available(val state: FirstPromiseOnboardingState) : FirstPromiseStateReadResult
    data object Corrupted : FirstPromiseStateReadResult
}

class FirstPromiseStateCorruptedException : IllegalStateException(
    "First-promise onboarding state could not be decoded",
)

private fun FirstPromiseStateReadResult.requireAvailable(): FirstPromiseOnboardingState = when (this) {
    is FirstPromiseStateReadResult.Available -> state
    FirstPromiseStateReadResult.Corrupted -> throw FirstPromiseStateCorruptedException()
}
