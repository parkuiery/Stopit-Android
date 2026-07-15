package com.uiery.keep.feature.onboarding.experiment

data class OnboardingExperimentSnapshot(
    val treatmentPercent: Int = 0,
    val newAssignmentEnabled: Boolean = false,
    val emergencyDisabled: Boolean = false,
    val remoteReadable: Boolean = false,
)

interface OnboardingExperimentConfig {
    fun snapshot(): OnboardingExperimentSnapshot
}
