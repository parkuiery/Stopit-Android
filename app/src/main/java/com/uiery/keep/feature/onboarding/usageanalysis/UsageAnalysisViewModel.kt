package com.uiery.keep.feature.onboarding.usageanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.usageinsight.OnboardingUsageProfileRepository
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.AnalysisLatencyBucket
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseRecommendationPolicy
import com.uiery.keep.domain.firstpromise.UsageCoverageBucket
import com.uiery.keep.domain.firstpromise.UsageDataQuality
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfilePolicy
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

data class UsageAnalysisUiState(val averageDailyMinutes: Long? = null)

sealed interface UsageAnalysisSideEffect {
    data class NavigateProposal(val proposal: TransientAnalysisProposal) : UsageAnalysisSideEffect
    data object NavigateManualAppSelect : UsageAnalysisSideEffect
}

@HiltViewModel
class UsageAnalysisViewModel internal constructor(
    private val analytics: KeepAnalytics,
    private val draftStore: FirstPromiseDraftStore,
    private val analyzeGoal: suspend (FirstPromiseGoal) -> OnboardingUsageProfileResult,
    private val dispatcher: CoroutineDispatcher,
    private val analyzerScope: CoroutineScope,
    private val timeout: Duration,
    private val elapsedRealtimeMillis: () -> Long,
    private val draftId: () -> String,
) : ViewModel(), ContainerHost<UsageAnalysisUiState, UsageAnalysisSideEffect> {
    @Inject constructor(
        analytics: KeepAnalytics,
        draftStore: FirstPromiseDraftStore,
        repository: OnboardingUsageProfileRepository,
    ) : this(
        analytics = analytics,
        draftStore = draftStore,
        analyzeGoal = { goal ->
            repository.profile(
                today = LocalDate.now(),
                zoneId = ZoneId.systemDefault(),
                goalDefaultStartMinutes = FirstPromiseRecommendationPolicy.defaultStartMinutes(goal),
            )
        },
        dispatcher = Dispatchers.IO,
        analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        timeout = 5.seconds,
        elapsedRealtimeMillis = { android.os.SystemClock.elapsedRealtime() },
        draftId = { UUID.randomUUID().toString() },
    )

    override val container: Container<UsageAnalysisUiState, UsageAnalysisSideEffect> = container(UsageAnalysisUiState())
    private val attemptMutex = Mutex()

    fun onStepViewed() {
        analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_USAGE_ANALYSIS)
    }

    fun startAnalysis() {
        FirstPromiseAnalysisTransientHolder.clear()
        viewModelScope.launch(dispatcher) {
            val attempt = attemptMutex.withLock {
                val next = (draftStore.readState().analysisAttemptId ?: 0L) + 1L
                if (draftStore.beginUsageAnalysis(next) is com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation.Changed) {
                    next to draftStore.readState().goal
                } else {
                    null
                }
            } ?: return@launch
            runAttempt(attempt.first, attempt.second)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun runAttempt(attemptId: Long, goal: FirstPromiseGoal) {
        val startedAt = elapsedRealtimeMillis()
        val deferred = analyzerScope.async { analyzeGoal(goal) }
        try {
            when (
                val race = select<AnalysisRace> {
                    deferred.onAwait { AnalysisRace.Completed(it) }
                    onTimeout(timeout.inWholeMilliseconds) { AnalysisRace.TimedOut }
                }
            ) {
                is AnalysisRace.Completed -> when (val result = race.result) {
                    is OnboardingUsageProfileResult.Ready -> handleReady(attemptId, goal, result, startedAt)
                    is OnboardingUsageProfileResult.Insufficient -> fallback(
                        attemptId = attemptId,
                        coverage = OnboardingUsageProfilePolicy.coverageBucket(result.usageCoverageDays),
                        latency = elapsedBucket(startedAt),
                    )
                }
                AnalysisRace.TimedOut -> {
                    deferred.cancel()
                    fallback(attemptId, UsageCoverageBucket.Zero, AnalysisLatencyBucket.Timeout)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            fallback(attemptId, UsageCoverageBucket.Zero, elapsedBucket(startedAt))
        }
    }

    private suspend fun handleReady(
        attemptId: Long,
        goal: FirstPromiseGoal,
        result: OnboardingUsageProfileResult.Ready,
        startedAt: Long,
    ) {
        val profile = result.profile
        val proposal = FirstPromiseRecommendationPolicy.fromProfile(draftId(), goal, profile)
        val reason = FirstPromiseRecommendationPolicy.toReasonRef(proposal)
        if (!draftStore.completeAnalysis(attemptId, proposal.draft, reason)) return
        analytics.trackUsageAnalysisCompleted(
            dataQuality = profile.dataQuality,
            patternType = profile.patternType,
            coverageDaysBucket = OnboardingUsageProfilePolicy.coverageBucket(profile.usageCoverageDays),
            latencyBucket = elapsedBucket(startedAt),
        )
        intent {
            reduce { state.copy(averageDailyMinutes = profile.averageDailyMinutes) }
            postSideEffect(
                UsageAnalysisSideEffect.NavigateProposal(
                    TransientAnalysisProposal(
                        draftId = proposal.draft.draftId,
                        averageDailyMinutes = profile.averageDailyMinutes,
                    ),
                ),
            )
        }
    }

    private suspend fun fallback(
        attemptId: Long,
        coverage: UsageCoverageBucket,
        latency: AnalysisLatencyBucket,
    ) {
        if (!draftStore.failAnalysis(attemptId)) return
        FirstPromiseAnalysisTransientHolder.clear()
        analytics.trackUsageAnalysisCompleted(
            dataQuality = UsageDataQuality.Insufficient,
            patternType = UsagePatternType.Manual,
            coverageDaysBucket = coverage,
            latencyBucket = latency,
        )
        intent { postSideEffect(UsageAnalysisSideEffect.NavigateManualAppSelect) }
    }

    private fun elapsedBucket(startedAt: Long): AnalysisLatencyBucket = when (
        (elapsedRealtimeMillis() - startedAt).coerceAtLeast(0L)
    ) {
        in 0..999 -> AnalysisLatencyBucket.UnderOneSecond
        in 1_000..2_999 -> AnalysisLatencyBucket.OneToThreeSeconds
        else -> AnalysisLatencyBucket.ThreeToFiveSeconds
    }

    override fun onCleared() {
        analyzerScope.cancel()
        super.onCleared()
    }

    private sealed interface AnalysisRace {
        data class Completed(val result: OnboardingUsageProfileResult) : AnalysisRace
        data object TimedOut : AnalysisRace
    }
}
