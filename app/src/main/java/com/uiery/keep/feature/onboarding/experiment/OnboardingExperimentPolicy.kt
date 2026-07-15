package com.uiery.keep.feature.onboarding.experiment

import com.uiery.keep.domain.firstpromise.OnboardingVariant

object OnboardingExperimentPolicy {
    fun assign(
        snapshot: OnboardingExperimentSnapshot,
        bucket: Int,
    ): OnboardingVariant {
        if (!snapshot.remoteReadable || !snapshot.newAssignmentEnabled) {
            return OnboardingVariant.Control
        }

        val treatmentPercent = snapshot.treatmentPercent.coerceIn(0, 100)
        return if (bucket in 0 until treatmentPercent) {
            OnboardingVariant.PromiseCoachV1
        } else {
            OnboardingVariant.Control
        }
    }
}
