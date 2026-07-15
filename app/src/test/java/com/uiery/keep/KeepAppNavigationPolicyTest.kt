package com.uiery.keep

import androidx.lifecycle.SavedStateHandle
import com.uiery.keep.feature.goallock.GoalLockCreationRoute
import com.uiery.keep.feature.goallock.GoalLockEditRoute
import com.uiery.keep.feature.goallock.consumeGoalLockEditSaved
import com.uiery.keep.feature.goallock.goalLockDetailAfterCreationNavOptions
import com.uiery.keep.feature.goallock.goalLockEditNavOptions
import com.uiery.keep.feature.goallock.markGoalLockEditSaved
import com.uiery.keep.feature.lockhistory.LockHistoryRoute
import com.uiery.keep.feature.onboarding.Onboarding
import com.uiery.keep.feature.onboarding.canonicalOnboardingStartDestination
import com.uiery.keep.feature.onboarding.defaultOnboardingLaunchDestination
import com.uiery.keep.feature.onboarding.entry.onboardingEntryNavOptions
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryBackStackPolicy
import com.uiery.keep.feature.onboarding.entry.OnboardingEntryDestination
import com.uiery.keep.feature.onboarding.entry.backStackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAppNavigationPolicyTest {
    @Test
    fun onboardingStartsAtTheZeroContentAssignmentEntry() {
        assertEquals(Onboarding.Route.Entry, canonicalOnboardingStartDestination())
    }

    @Test
    fun noArgumentOnboardingNavigationTargetsEntryInsteadOfBypassingAssignment() {
        assertEquals(Onboarding.Route.Entry, defaultOnboardingLaunchDestination())
    }

    @Test
    fun onboardingEntryIsRemovedAfterItsSingleRoutingDecision() {
        val navOptions = onboardingEntryNavOptions()

        assertEquals(Onboarding.Route.Entry::class, navOptions.popUpToRouteClass)
        assertTrue(navOptions.isPopUpToInclusive())
    }

    @Test
    fun completedOnboardingUsesCanonicalHomeRootClearWhileOnboardingRoutesOnlyRemoveEntry() {
        assertEquals(
            OnboardingEntryBackStackPolicy.ClearRootForHome,
            OnboardingEntryDestination.Home.backStackPolicy(),
        )
        OnboardingEntryDestination.entries
            .filterNot { it == OnboardingEntryDestination.Home }
            .forEach { destination ->
                assertEquals(
                    "destination=$destination",
                    OnboardingEntryBackStackPolicy.RemoveEntry,
                    destination.backStackPolicy(),
                )
            }
    }

    @Test
    fun historyDomainUsesLockHistoryAsCanonicalTopLevelRoute() {
        assertEquals(LockHistoryRoute, canonicalHistoryRoute())
        assertFalse(shouldRegisterLegacyHistoryRoute())
    }

    @Test
    fun goalLockCreationUsesDedicatedTopLevelEntryRoute() {
        assertEquals(GoalLockCreationRoute, canonicalGoalLockCreationRoute())
        assertTrue(shouldRegisterGoalLockCreationEntryRoute())
    }

    @Test
    fun goalLockDetailAfterCreationRemovesCreationRouteFromBackStack() {
        val navOptions = goalLockDetailAfterCreationNavOptions()

        assertEquals(GoalLockCreationRoute::class, navOptions.popUpToRouteClass)
        assertTrue(navOptions.isPopUpToInclusive())
    }

    @Test
    fun goalLockEditRouteCarriesGoalIdWithoutReplacingDetail() {
        assertEquals(42L, GoalLockEditRoute(goalLockId = 42L).goalLockId)
    }

    @Test
    fun goalLockEditPreventsDuplicateDestinationOnRapidTaps() {
        assertTrue(goalLockEditNavOptions().shouldLaunchSingleTop())
    }

    @Test
    fun goalLockEditSavedResultIsConsumedOnlyOnce() {
        val savedStateHandle = SavedStateHandle()

        savedStateHandle.markGoalLockEditSaved()

        assertTrue(savedStateHandle.consumeGoalLockEditSaved())
        assertFalse(savedStateHandle.consumeGoalLockEditSaved())
    }

    @Test
    fun devToolRouteIsAvailableOnlyForDevDebugBuilds() {
        assertTrue(
            shouldRegisterDevToolRoute(
                flavor = "dev",
                isDebug = true,
            )
        )

        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "prod",
                isDebug = true,
            )
        )
        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "prod",
                isDebug = false,
            )
        )
        assertFalse(
            shouldRegisterDevToolRoute(
                flavor = "dev",
                isDebug = false,
            )
        )
    }
}
