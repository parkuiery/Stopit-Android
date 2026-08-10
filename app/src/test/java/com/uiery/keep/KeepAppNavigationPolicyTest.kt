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
    fun theOnlyScreenThatCanBeTheGraphRootHasSomewhereToGoUpTo() {
        // 알림 탭과 차단 화면의 루틴 제안은 루틴 화면을 그래프의 시작 지점으로 만든다. 그때
        // navigateUp() 은 되돌아갈 항목이 없어 조용히 false 를 돌려주고 아무 일도 하지 않아,
        // 사용자에게는 뒤로가기 버튼이 죽은 것으로 보인다. 실기기에서 재현된 증상이다.
        val source = java.io.File("src/main/java/com/uiery/keep/KeepApp.kt").readText()

        val routineScreen = source.indexOf("routineScreen(")
        assertTrue("루틴 화면이 그래프에 등록되어 있어야 한다", routineScreen >= 0)
        val wiring = source.substring(routineScreen, source.indexOf(")", source.indexOf("onNavigateLock", routineScreen)))
        assertTrue(
            "루틴 화면의 뒤로가기는 올라갈 곳이 없을 때 홈으로 떨어지는 경로를 써야 한다. " +
                "navigateUp 만 쓰면 시작 지점일 때 아무 일도 일어나지 않는다.",
            wiring.contains("onNavigateBack = navigateUpOrHome"),
        )
        assertTrue(
            "대체 경로는 navigateUp 이 실패했을 때만 홈으로 보내야 한다",
            source.contains("if (!navController.navigateUp()) {"),
        )

        // 시작 지점이 될 수 있는 화면이 늘면 같은 구멍이 생긴다.
        val startDestinations = java.io.File("src/main/java/com/uiery/keep/MainActivity.kt").readText()
        assertEquals(
            "시작 지점이 될 수 있는 화면은 루틴 하나여야 한다. 늘어났다면 그 화면에도 " +
                "navigateUpOrHome 을 물려야 한다.",
            1,
            Regex("""return if \(routineId != null\) RoutineRoute\(\) else SplashRoute""")
                .findAll(startDestinations).count(),
        )
    }

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
