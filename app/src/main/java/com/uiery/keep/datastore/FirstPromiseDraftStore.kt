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
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.FirstPromiseStatePolicy
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PendingSystemAction
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePermissionOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class FirstPromiseDraftStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun readState(): FirstPromiseOnboardingState =
        decode(dataStore.data.first()[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])

    suspend fun assignIfAbsent(
        variant: OnboardingVariant,
        version: OnboardingAssignmentVersion,
    ): FirstPromiseOnboardingState {
        val current = readState()
        val initialMutation = FirstPromiseStatePolicy.assignIfAbsent(current, variant, version)
        if (initialMutation !is FirstPromiseStateMutation.Changed) {
            return current
        }

        var result = current
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            result = when (val mutation = FirstPromiseStatePolicy.assignIfAbsent(latest, variant, version)) {
                is FirstPromiseStateMutation.Changed -> mutation.state.also { preferences.write(it) }
                else -> latest
            }
        }
        return result
    }

    suspend fun transitionTo(target: FirstPromisePhase): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.transition(it, target) }

    suspend fun selectGoal(
        goal: FirstPromiseGoal,
        path: FirstPromisePath,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.selectGoal(it, goal, path) }

    suspend fun markMilestone(milestone: FirstPromiseMilestone): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.markMilestone(it, milestone) }

    suspend fun storeDraft(
        draft: FirstPromiseDraft,
        reason: RecommendationReasonRef,
    ): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.storeDraft(it, draft, reason) }

    suspend fun setPendingSystemAction(action: PendingSystemAction): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.setPendingSystemAction(it, action) }

    suspend fun clearPendingSystemAction(): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.setPendingSystemAction(it, null) }

    suspend fun beginAnalysisAttempt(attemptId: Long): FirstPromiseStateMutation =
        applyMutation { FirstPromiseStatePolicy.beginAnalysisAttempt(it, attemptId) }

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

    suspend fun applyEmergency(): FirstPromiseEmergencyResult =
        applyEmergencyMutation(FirstPromiseStatePolicy::applyEmergency)

    suspend fun resolveEmergencyPersistence(
        resolution: FirstPromisePersistenceResolution,
    ): FirstPromiseEmergencyResult =
        applyEmergencyMutation { FirstPromiseStatePolicy.resolveEmergencyPersistence(it, resolution) }

    suspend fun beginUsagePermissionAttempt(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.beginUsagePermissionAttempt(it, attemptId) } is
            FirstPromiseStateMutation.Changed

    suspend fun beginManualUsagePermissionAttempt(attemptId: Long): Boolean =
        applyMutation { FirstPromiseStatePolicy.beginUsagePermissionAttempt(it, attemptId, manual = true) } is
            FirstPromiseStateMutation.Changed

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
    ): FirstPromiseStateMutation {
        val initialMutation = policy(readState())
        if (initialMutation !is FirstPromiseStateMutation.Changed) {
            return initialMutation
        }

        var result: FirstPromiseStateMutation = initialMutation
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            result = policy(latest)
            (result as? FirstPromiseStateMutation.Changed)?.state?.let { state ->
                preferences.write(state)
            }
        }
        return result
    }

    private suspend fun applyEmergencyMutation(
        policy: (FirstPromiseOnboardingState) -> FirstPromiseEmergencyResult,
    ): FirstPromiseEmergencyResult {
        val current = readState()
        val initialResult = policy(current)
        if (initialResult.state == current) {
            return initialResult
        }

        var result = initialResult
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE])
            result = policy(latest)
            if (result.state != latest) {
                preferences.write(result.state)
            }
        }
        return result
    }

    private fun MutablePreferences.write(state: FirstPromiseOnboardingState) {
        this[PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE] = json.encodeToString(state)
    }

    private fun decode(value: String?): FirstPromiseOnboardingState =
        value?.let { runCatching { json.decodeFromString<FirstPromiseOnboardingState>(it) }.getOrNull() }
            ?: FirstPromiseOnboardingState()
}
