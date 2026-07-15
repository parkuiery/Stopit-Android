package com.uiery.keep.feature.onboarding.usageanalysis

import com.uiery.keep.domain.firstpromise.AnalysisLatencyBucket
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.domain.usageinsight.InsufficientReason
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfile
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.onboarding.firstPromiseStore
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAnalysisViewModelTest {
    @Test
    fun fullAndUsageOnlyPersistTypedProposalAndReportReturnedEvidence() = runBlocking {
        listOf(UsageDataQuality.Full to UsagePatternType.Night, UsageDataQuality.UsageOnly to UsagePatternType.TopApp).forEach { (quality, pattern) ->
            val analytics = FirstPromiseRecordingAnalytics()
            val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Sleep)
            val viewModel = viewModel(analytics, store) { ready(quality, pattern) }
            viewModel.startAnalysis(); delay(100)
            assertEquals(FirstPromisePhase.DraftReady, store.readState().phase)
            assertEquals("com.example.video", store.readState().draft?.packageName)
            assertEquals(42L, viewModel.container.stateFlow.value.averageDailyMinutes)
            val event = analytics.calls.filterIsInstance<FirstPromiseAnalyticsCall.Analysis>().single()
            assertEquals(quality, event.quality)
            assertEquals(pattern, event.pattern)
            assertEquals(UsageCoverageBucket.ThreeSix, event.coverage)
            assertEquals(AnalysisLatencyBucket.UnderOneSecond, event.latency)
        }
    }

    @Test
    fun insufficientExceptionAndTimeoutFallBackWithoutFakeProposal() = runBlocking {
        val cases = listOf<suspend () -> OnboardingUsageProfileResult>(
            { OnboardingUsageProfileResult.Insufficient(2, 1, InsufficientReason.InsufficientUsageCoverage) },
            { error("query failed") },
            { delay(200); ready(UsageDataQuality.Full, UsagePatternType.Night) },
        )
        cases.forEachIndexed { index, analyzer ->
            val analytics = FirstPromiseRecordingAnalytics()
            val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
            viewModel(analytics, store, timeout = 30.milliseconds, analyzer = analyzer).startAnalysis()
            delay(100)
            val state = store.readState()
            assertEquals(FirstPromisePhase.ManualSelectPending, state.phase)
            assertNull(state.draft)
            val event = analytics.calls.filterIsInstance<FirstPromiseAnalyticsCall.Analysis>().single()
            if (index == 2) {
                assertEquals(FirstPromiseAnalyticsCall.Analysis(UsageDataQuality.Insufficient, UsagePatternType.Manual, UsageCoverageBucket.Zero, AnalysisLatencyBucket.Timeout), event)
            }
        }
    }

    @Test
    fun lateOlderAttemptCannotWriteStateAnalyticsOrNavigation() = runBlocking {
        val first = CompletableDeferred<OnboardingUsageProfileResult>()
        var calls = 0
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Study)
        val viewModel = viewModel(analytics, store, timeout = 1.seconds) {
            if (calls++ == 0) first.await() else ready(UsageDataQuality.UsageOnly, UsagePatternType.TopApp)
        }
        viewModel.startAnalysis(); delay(20)
        viewModel.startAnalysis(); delay(100)
        first.complete(ready(UsageDataQuality.Full, UsagePatternType.Night)); delay(50)
        assertEquals(UsagePatternType.TopApp, store.readState().recommendationReasonRef?.patternType)
        assertEquals(1, analytics.calls.filterIsInstance<FirstPromiseAnalyticsCall.Analysis>().size)
    }

    @Test
    fun successfulNavigationStoresExactAverageOnlyInProcessMemory() = runBlocking {
        FirstPromiseAnalysisTransientHolder.clear()
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Sleep)
        val viewModel = viewModel(analytics, store) { ready(UsageDataQuality.Full, UsagePatternType.Night) }
        val effect = async { viewModel.container.sideEffectFlow.first() }

        viewModel.startAnalysis()
        val navigation = effect.await() as UsageAnalysisSideEffect.NavigateProposal
        var navigated = false
        storeTransientAverageThenNavigate(navigation.proposal) { navigated = true }

        assertEquals(42L, FirstPromiseAnalysisTransientHolder.peek("draft-1"))
        assertEquals(true, navigated)
        assertEquals(null, FirstPromiseAnalysisTransientHolder.peek("another-draft"))
        FirstPromiseAnalysisTransientHolder.clear()
        assertEquals(null, FirstPromiseAnalysisTransientHolder.peek("draft-1"))
    }

    @Test
    fun timeoutDoesNotWaitForANonCooperativeAnalyzerAndIgnoresItsLateResult() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val analytics = FirstPromiseRecordingAnalytics()
        val store = firstPromiseStore(FirstPromisePhase.UsageAccessPending, FirstPromiseGoal.Focus)
        val viewModel = viewModel(analytics, store, timeout = 30.milliseconds) {
            started.countDown()
            release.await()
            ready(UsageDataQuality.Full, UsagePatternType.Night)
        }
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        try {
            viewModel.startAnalysis()
            assertEquals(true, started.await(1, TimeUnit.SECONDS))
            assertEquals(
                UsageAnalysisSideEffect.NavigateManualAppSelect,
                withTimeout(1_000) { navigation.await() },
            )
            assertEquals(FirstPromisePhase.ManualSelectPending, store.readState().phase)
        } finally {
            release.countDown()
        }
        delay(100)
        assertEquals(1, analytics.calls.filterIsInstance<FirstPromiseAnalyticsCall.Analysis>().size)
        assertNull(store.readState().draft)
    }

    @Test
    fun draftMismatchDiscardsStaleTransientProposal() {
        FirstPromiseAnalysisTransientHolder.store(TransientAnalysisProposal("old", 42))

        assertNull(FirstPromiseAnalysisTransientHolder.consume("new"))
        assertNull(FirstPromiseAnalysisTransientHolder.peek("old"))
    }

    private fun viewModel(
        analytics: FirstPromiseRecordingAnalytics,
        store: com.uiery.keep.datastore.FirstPromiseDraftStore,
        timeout: Duration = 5.seconds,
        analyzer: suspend () -> OnboardingUsageProfileResult,
    ) = UsageAnalysisViewModel(
        analytics = analytics,
        draftStore = store,
        analyzeGoal = { analyzer() },
        dispatcher = Dispatchers.Unconfined,
        analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        timeout = timeout,
        elapsedRealtimeMillis = AtomicLong(0)::getAndIncrement,
        draftId = { "draft-1" },
    )

    private fun ready(quality: UsageDataQuality, pattern: UsagePatternType) = OnboardingUsageProfileResult.Ready(
        OnboardingUsageProfile(
            packageName = "com.example.video",
            appLabel = "Video",
            averageDailyMinutes = 42,
            suggestedStartMinutes = 23 * 60,
            usageCoverageDays = 4,
            eventCoverageDays = if (quality == UsageDataQuality.Full) 4 else 1,
            dataQuality = quality,
            patternType = pattern,
        ),
    )
}
