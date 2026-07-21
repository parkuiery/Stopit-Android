package com.uiery.keep.feature.onboarding.experiment

import com.uiery.keep.domain.firstpromise.OnboardingVariant

data class OnboardingExperimentResolution(
    val variant: OnboardingVariant = OnboardingVariant.Control,
    val remoteReadable: Boolean = false,
)

interface OnboardingExperimentConfig {
    suspend fun resolve(): OnboardingExperimentResolution
}
