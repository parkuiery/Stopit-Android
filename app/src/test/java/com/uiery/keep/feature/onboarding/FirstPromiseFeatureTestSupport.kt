package com.uiery.keep.feature.onboarding

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.AnalysisLatencyBucket
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal sealed interface FirstPromiseAnalyticsCall {
    data class Screen(val value: String) : FirstPromiseAnalyticsCall
    data class StepView(val value: String) : FirstPromiseAnalyticsCall
    data class StepComplete(val value: String) : FirstPromiseAnalyticsCall
    data class Exposure(val variant: OnboardingVariant) : FirstPromiseAnalyticsCall
    data object AppSelectionCompletedSingle : FirstPromiseAnalyticsCall
    data class Permission(val name: String, val outcome: String, val step: String?) : FirstPromiseAnalyticsCall
    data class Analysis(
        val quality: UsageDataQuality,
        val pattern: UsagePatternType,
        val coverage: UsageCoverageBucket,
        val latency: AnalysisLatencyBucket,
    ) : FirstPromiseAnalyticsCall
}

internal open class FirstPromiseRecordingAnalytics : KeepAnalytics {
    val calls = mutableListOf<FirstPromiseAnalyticsCall>()

    override fun logEvent(name: String, params: Map<String, Any?>) = Unit
    override fun logScreenView(screenName: String) { calls += FirstPromiseAnalyticsCall.Screen(screenName) }
    override fun setUserProperty(name: String, value: String) = Unit
    override fun trackFirstOpen() = Unit
    override fun trackOnboardingStepView(stepName: String) { calls += FirstPromiseAnalyticsCall.StepView(stepName) }
    override fun trackOnboardingStepComplete(stepName: String) { calls += FirstPromiseAnalyticsCall.StepComplete(stepName) }
    override fun trackOnboardingExperimentExposed(variant: OnboardingVariant, assignmentVersion: OnboardingAssignmentVersion) {
        calls += FirstPromiseAnalyticsCall.Exposure(variant)
    }
    override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) {
        calls += FirstPromiseAnalyticsCall.Permission(permissionName, outcome, stepName)
    }
    override fun trackUsageAnalysisCompleted(
        dataQuality: UsageDataQuality,
        patternType: UsagePatternType,
        coverageDaysBucket: UsageCoverageBucket,
        latencyBucket: AnalysisLatencyBucket,
    ) {
        calls += FirstPromiseAnalyticsCall.Analysis(dataQuality, patternType, coverageDaysBucket, latencyBucket)
    }
    override fun trackAppSelectionCompleted(selectedAppCount: Int, isOnboarding: Boolean) {
        if (selectedAppCount == 1 && isOnboarding) {
            calls += FirstPromiseAnalyticsCall.AppSelectionCompletedSingle
        }
    }
    override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
    override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
    override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
    override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
}

internal fun firstPromiseStore(
    phase: FirstPromisePhase,
    goal: FirstPromiseGoal = FirstPromiseGoal.Unspecified,
): FirstPromiseDraftStore {
    val state = FirstPromiseOnboardingState(
        assignment = OnboardingVariant.PromiseCoachV1,
        assignmentVersion = OnboardingAssignmentVersion.V1,
        phase = phase,
        goal = goal,
    )
    return FirstPromiseDraftStore(
        FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            ),
        ),
    )
}

internal fun firstPromiseStore(state: FirstPromiseOnboardingState): FirstPromiseDraftStore =
    FirstPromiseDraftStore(
        FakeDataStore(
            mutablePreferencesOf(
                PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(state),
            ),
        ),
    )
