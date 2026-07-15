package com.uiery.keep.feature.onboarding.experiment

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val TREATMENT_PERCENT_KEY = "onboarding_promise_coach_percent"
private const val NEW_ASSIGNMENT_ENABLED_KEY = "onboarding_promise_coach_new_assignment_enabled"
private const val EMERGENCY_DISABLED_KEY = "onboarding_promise_coach_emergency_disabled"

@Singleton
class FirebaseOnboardingExperimentConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : OnboardingExperimentConfig {
    override fun snapshot(): OnboardingExperimentSnapshot {
        return try {
            val treatmentPercent = remoteConfig.getValue(TREATMENT_PERCENT_KEY)
            val newAssignmentEnabled = remoteConfig.getValue(NEW_ASSIGNMENT_ENABLED_KEY)
            val emergencyDisabled = remoteConfig.getValue(EMERGENCY_DISABLED_KEY)
            val values = listOf(treatmentPercent, newAssignmentEnabled, emergencyDisabled)
            if (values.any { it.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }) {
                OnboardingExperimentSnapshot()
            } else {
                OnboardingExperimentSnapshot(
                    treatmentPercent = Math.toIntExact(treatmentPercent.asLong()),
                    newAssignmentEnabled = newAssignmentEnabled.asBoolean(),
                    emergencyDisabled = emergencyDisabled.asBoolean(),
                    remoteReadable = true,
                )
            }
        } catch (_: Exception) {
            OnboardingExperimentSnapshot()
        }
    }
}
