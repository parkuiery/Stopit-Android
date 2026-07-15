package com.uiery.keep.feature.onboarding.experiment

import com.uiery.keep.domain.firstpromise.OnboardingVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingExperimentPolicyTest {
    @Test
    fun snapshotDefaultsAreControlSafe() {
        assertEquals(
            OnboardingExperimentSnapshot(
                treatmentPercent = 0,
                newAssignmentEnabled = false,
                emergencyDisabled = false,
                remoteReadable = false,
            ),
            OnboardingExperimentSnapshot(),
        )
    }

    @Test
    fun unreadableRemoteConfigAlwaysAssignsControl() {
        val snapshot = OnboardingExperimentSnapshot(
            treatmentPercent = 100,
            newAssignmentEnabled = true,
            remoteReadable = false,
        )

        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(snapshot, bucket = 0))
        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(snapshot, bucket = 99))
    }

    @Test
    fun disabledNewAssignmentsAlwaysAssignControl() {
        val snapshot = OnboardingExperimentSnapshot(
            treatmentPercent = 100,
            newAssignmentEnabled = false,
            remoteReadable = true,
        )

        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(snapshot, bucket = 0))
    }

    @Test
    fun rolloutBoundaryUsesBucketsFromZeroThroughPercentExclusive() {
        val snapshot = OnboardingExperimentSnapshot(
            treatmentPercent = 10,
            newAssignmentEnabled = true,
            remoteReadable = true,
        )

        assertEquals(OnboardingVariant.PromiseCoachV1, OnboardingExperimentPolicy.assign(snapshot, bucket = 0))
        assertEquals(OnboardingVariant.PromiseCoachV1, OnboardingExperimentPolicy.assign(snapshot, bucket = 9))
        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(snapshot, bucket = 10))
        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(snapshot, bucket = 99))
    }

    @Test
    fun rolloutPercentIsClampedToZeroThroughOneHundred() {
        val enabledSnapshot = OnboardingExperimentSnapshot(
            treatmentPercent = -1,
            newAssignmentEnabled = true,
            remoteReadable = true,
        )
        val fullyEnabledSnapshot = enabledSnapshot.copy(treatmentPercent = 101)

        assertEquals(OnboardingVariant.Control, OnboardingExperimentPolicy.assign(enabledSnapshot, bucket = 0))
        assertEquals(OnboardingVariant.PromiseCoachV1, OnboardingExperimentPolicy.assign(fullyEnabledSnapshot, bucket = 99))
    }
}
