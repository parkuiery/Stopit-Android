package com.uiery.keep.feature.onboarding.experiment

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val TREATMENT_PERCENT_KEY = "onboarding_promise_coach_percent"
private const val NEW_ASSIGNMENT_ENABLED_KEY = "onboarding_promise_coach_new_assignment_enabled"
private const val EMERGENCY_DISABLED_KEY = "onboarding_promise_coach_emergency_disabled"

private val CONTROL_SAFE_DEFAULTS = mapOf<String, Any>(
    TREATMENT_PERCENT_KEY to 0L,
    NEW_ASSIGNMENT_ENABLED_KEY to false,
    EMERGENCY_DISABLED_KEY to false,
)

@Singleton
class FirebaseOnboardingExperimentConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : OnboardingExperimentConfig {
    init {
        remoteConfig.setDefaultsAsync(CONTROL_SAFE_DEFAULTS)
        remoteConfig.fetchAndActivate()
    }

    override fun snapshot(): OnboardingExperimentSnapshot = try {
        OnboardingExperimentSnapshot(
            treatmentPercent = remoteConfig.getLong(TREATMENT_PERCENT_KEY).toInt(),
            newAssignmentEnabled = remoteConfig.getBoolean(NEW_ASSIGNMENT_ENABLED_KEY),
            emergencyDisabled = remoteConfig.getBoolean(EMERGENCY_DISABLED_KEY),
            remoteReadable = true,
        )
    } catch (_: Exception) {
        OnboardingExperimentSnapshot()
    }
}
