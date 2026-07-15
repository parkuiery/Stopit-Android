package com.uiery.keep

import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.feature.routine.RoutineRoute
import com.uiery.keep.feature.splash.SplashRoute
import com.uiery.keep.notification.NotificationHelper
import com.uiery.keep.data.firstpromise.FirstPromiseOutboxDispatcher
import com.uiery.keep.data.firstpromise.FirstPromiseStartupRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityTest {
    @Test
    fun firstPromiseStartupDrainsPendingRowsThenRunsRetentionCleanup() = runBlocking {
        val dispatcher = StartupRecordingDispatcher()

        FirstPromiseStartupRunner(dispatcher).run()

        assertEquals(listOf("drain", "cleanup"), dispatcher.calls)
    }

    @Test
    fun mainStartDestinationRoutesRoutineStartNotificationTapToRoutineScreen() {
        assertEquals(
            RoutineRoute(),
            createMainStartDestination(
                action = NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP,
                routineId = 42L,
                repeatBlockSurface = null,
                repeatBlockReason = null,
                repeatBlockTimeBucket = null,
                repeatBlockDayType = null,
                repeatBlockCategoryBucket = null,
                repeatBlockCountBucket = null,
                repeatBlockCoverageState = null,
                prefillPackages = emptyList(),
                prefillStartHour = null,
                prefillStartMinute = null,
                prefillEndHour = null,
                prefillEndMinute = null,
            ),
        )
    }

    @Test
    fun mainStartDestinationFallsBackToSplashForMalformedRoutineStartNotificationTap() {
        assertEquals(
            SplashRoute,
            createMainStartDestination(
                action = NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP,
                routineId = null,
                repeatBlockSurface = null,
                repeatBlockReason = null,
                repeatBlockTimeBucket = null,
                repeatBlockDayType = null,
                repeatBlockCategoryBucket = null,
                repeatBlockCountBucket = null,
                repeatBlockCoverageState = null,
                prefillPackages = emptyList(),
                prefillStartHour = null,
                prefillStartMinute = null,
                prefillEndHour = null,
                prefillEndMinute = null,
            ),
        )
    }

    @Test
    fun newIntentDestinationRoutesRoutineStartNotificationTapToRoutineScreen() {
        assertEquals(
            RoutineRoute(),
            createMainNewIntentDestination(
                action = NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP,
                routineId = 42L,
                repeatBlockSurface = null,
                repeatBlockReason = null,
                repeatBlockTimeBucket = null,
                repeatBlockDayType = null,
                repeatBlockCategoryBucket = null,
                repeatBlockCountBucket = null,
                repeatBlockCoverageState = null,
                prefillPackages = emptyList(),
                prefillStartHour = null,
                prefillStartMinute = null,
                prefillEndHour = null,
                prefillEndMinute = null,
            ),
        )
    }

    @Test
    fun newIntentDestinationIgnoresMalformedRoutineStartNotificationTap() {
        assertNull(
            createMainNewIntentDestination(
                action = NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP,
                routineId = null,
                repeatBlockSurface = null,
                repeatBlockReason = null,
                repeatBlockTimeBucket = null,
                repeatBlockDayType = null,
                repeatBlockCategoryBucket = null,
                repeatBlockCountBucket = null,
                repeatBlockCoverageState = null,
                prefillPackages = emptyList(),
                prefillStartHour = null,
                prefillStartMinute = null,
                prefillEndHour = null,
                prefillEndMinute = null,
            ),
        )
    }

    @Test
    fun repeatBlockRoutineRouteRequiresCompletePostBlockPrefillExtras() {
        assertNull(
            createRepeatBlockRoutineRoute(
                repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                repeatBlockReason = "rapid_retry",
                repeatBlockTimeBucket = "night",
                repeatBlockDayType = "daily",
                repeatBlockCategoryBucket = "social",
                repeatBlockCountBucket = "3_5",
                repeatBlockCoverageState = "not_covered",
                prefillPackages = emptyList(),
                prefillStartHour = 23,
                prefillStartMinute = 0,
                prefillEndHour = 0,
                prefillEndMinute = 0,
            ),
        )
    }

    @Test
    fun repeatBlockRoutineRoutePreservesPostBlockPrefillExtras() {
        val route = createRepeatBlockRoutineRoute(
            repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
            repeatBlockReason = "rapid_retry",
            repeatBlockTimeBucket = "night",
            repeatBlockDayType = "daily",
            repeatBlockCategoryBucket = "social",
            repeatBlockCountBucket = "3_5",
            repeatBlockCoverageState = "not_covered",
            prefillPackages = listOf("com.instagram.android"),
            prefillStartHour = 23,
            prefillStartMinute = 0,
            prefillEndHour = 0,
            prefillEndMinute = 0,
        )

        assertEquals(
            RoutineRoute(
                repeatBlockSurface = RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS,
                repeatBlockReason = "rapid_retry",
                repeatBlockTimeBucket = "night",
                repeatBlockDayType = "daily",
                repeatBlockCategoryBucket = "social",
                repeatBlockCountBucket = "3_5",
                repeatBlockCoverageState = "not_covered",
                prefillPackages = listOf("com.instagram.android"),
                prefillStartHour = 23,
                prefillStartMinute = 0,
                prefillEndHour = 0,
                prefillEndMinute = 0,
            ),
            route,
        )
    }
}

private class StartupRecordingDispatcher : FirstPromiseOutboxDispatcher {
    val calls = mutableListOf<String>()
    override suspend fun drainAll() { calls += "drain" }
    override suspend fun drainDraft(draftId: String) = Unit
    override suspend fun cleanupSentRows() { calls += "cleanup" }
    override suspend fun creationEventsSent(draftId: String): Boolean = false
}
