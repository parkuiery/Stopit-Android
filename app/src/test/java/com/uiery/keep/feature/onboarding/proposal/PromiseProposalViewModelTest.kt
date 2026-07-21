package com.uiery.keep.feature.onboarding.proposal

import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.FirstPromisePath
import com.uiery.keep.domain.firstpromise.FirstPromisePhase
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.domain.firstpromise.PromiseEditField
import com.uiery.keep.domain.firstpromise.RecommendationReasonRef
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import com.uiery.keep.feature.onboarding.firstPromiseStore
import com.uiery.keep.feature.onboarding.usageanalysis.FirstPromiseAnalysisTransientHolder
import com.uiery.keep.feature.onboarding.usageanalysis.TransientAnalysisProposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseProposalViewModelTest {
    @Test
    fun `average fact distinguishes seven complete days from partial recorded coverage`() {
        assertEquals(
            ProposalFactCopyVariant.AverageSevenDays,
            proposalFactCopyVariant(ProposalFactType.Average, usageCoverageDays = 7),
        )
        assertEquals(
            ProposalFactCopyVariant.AveragePartialCoverage,
            proposalFactCopyVariant(ProposalFactType.Average, usageCoverageDays = 4),
        )
        assertTrue(shouldShowUsageEstimateNote(ProposalFactType.Average))
        assertTrue(shouldShowUsageEstimateNote(ProposalFactType.Coverage))
        assertFalse(shouldShowUsageEstimateNote(ProposalFactType.Neutral))
    }

    @Test
    fun `proposal shows one stored fact and one editable action from the same evidence`() = runBlocking {
        FirstPromiseAnalysisTransientHolder.store(TransientAnalysisProposal("draft-1", 102))
        val store = firstPromiseStore(personalizedState())
        val viewModel = viewModel(store = store)

        viewModel.onStepViewed()
        delay(50)

        val ui = viewModel.container.stateFlow.value
        assertEquals("Video", ui.appLabel)
        assertEquals(102L, ui.averageDailyMinutes)
        assertEquals(ProposalFactType.Average, ui.factType)
        assertEquals(UsagePatternType.Night, ui.patternType)
        assertEquals(23 * 60, ui.startMinutes)
        assertEquals((1..7).toSet(), ui.repeatDays)
        assertTrue(ui.canStart)
        assertFalse(ui.isPickerVisible)
        assertEquals("com.example.video", viewModel.selectedAppPackageName())
        assertNull(FirstPromiseAnalysisTransientHolder.peek("draft-1"))
    }

    @Test
    fun `editing app start and repeat days persists the draft and emits only typed edit fields`() = runBlocking {
        val analytics = ProposalRecordingAnalytics()
        val store = firstPromiseStore(personalizedState())
        val viewModel = viewModel(store, analytics)

        viewModel.changeApp("com.example.chat", "Chat")
        viewModel.changeStartMinutes(22 * 60 + 30)
        viewModel.changeRepeatDays((1..6).toSet())
        delay(100)

        val state = store.readState()
        assertEquals("com.example.chat", state.draft?.packageName)
        assertEquals("Chat", state.draft?.appLabel)
        assertEquals(22 * 60 + 30, state.draft?.startMinutes)
        assertEquals((1..6).toSet(), state.draft?.repeatDays)
        assertEquals(state.draft?.startMinutes, state.recommendationReasonRef?.selectedStartMinutes)
        assertEquals(
            listOf(PromiseEditField.App, PromiseEditField.StartTime, PromiseEditField.RepeatDays),
            analytics.edits,
        )
        assertEquals(FirstPromisePhase.DraftReady, state.phase)
        assertEquals(ProposalFactType.Neutral, viewModel.container.stateFlow.value.factType)
        assertNull(viewModel.container.stateFlow.value.averageDailyMinutes)
        assertTrue(FirstPromiseMilestone.ProposalAppEdited in state.trackedMilestones)
        assertEquals("com.example.chat", viewModel.selectedAppPackageName())
    }

    @Test
    fun `edited personalized app never rehydrates old evidence after recreation`() = runBlocking {
        val store = firstPromiseStore(personalizedState())
        val first = viewModel(store, rehydrateAverage = { 102 })
        first.onStepViewed(); delay(50)
        first.changeApp("com.example.chat", "Chat"); delay(50)

        var rehydrateCalls = 0
        val recreated = viewModel(store, rehydrateAverage = { rehydrateCalls++; 88 })
        recreated.onStepViewed(); delay(50)

        assertEquals(0, rehydrateCalls)
        assertEquals(ProposalFactType.Neutral, recreated.container.stateFlow.value.factType)
        assertNull(recreated.container.stateFlow.value.averageDailyMinutes)
        assertEquals("Chat", recreated.container.stateFlow.value.appLabel)
    }

    @Test
    fun `manual and goal-template drafts never display numeric observations`() = runBlocking {
        listOf(FirstPromiseSource.Manual, FirstPromiseSource.GoalTemplate).forEach { source ->
            FirstPromiseAnalysisTransientHolder.store(TransientAnalysisProposal("draft-1", 999))
            val state = personalizedState().copy(
                path = FirstPromisePath.Manual,
                draft = personalizedState().draft?.copy(source = source),
            )
            val store = firstPromiseStore(state)
            val viewModel = viewModel(store, rehydrateAverage = { 999 })

            viewModel.onStepViewed(); delay(50)

            assertEquals(ProposalFactType.Neutral, viewModel.container.stateFlow.value.factType)
            assertNull(viewModel.container.stateFlow.value.averageDailyMinutes)
        }
    }

    @Test
    fun `view and start milestones survive retries and personalized app selection is consumed once`() = runBlocking {
        val analytics = ProposalRecordingAnalytics()
        val store = firstPromiseStore(personalizedState())
        val dispatcher = FirstPromiseOnboardingAnalyticsDispatcher(store, analytics)
        val viewModel = viewModel(store, analytics, dispatcher)
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.onStepViewed()
        viewModel.onStepViewed()
        delay(50)
        viewModel.startFirstPromise()
        viewModel.startFirstPromise()

        assertEquals(PromiseProposalSideEffect.NavigateAccessibility, navigation.await())
        val state = store.readState()
        assertEquals(FirstPromisePhase.AccessibilityPending, state.phase)
        assertTrue(FirstPromiseMilestone.PromiseProposalView in state.trackedMilestones)
        assertTrue(FirstPromiseMilestone.RecommendationShown in state.trackedMilestones)
        assertTrue(FirstPromiseMilestone.PromiseProposalCompletion in state.trackedMilestones)
        assertTrue(FirstPromiseMilestone.AppSelection in state.trackedMilestones)
        assertEquals(1, analytics.recommendationShownCount)
        assertEquals(1, analytics.proposalViewCount)
        assertEquals(1, analytics.proposalCompleteCount)
        assertEquals(1, analytics.selectCompleteCount)
        assertEquals(1, analytics.appSelectionCount)
    }

    @Test
    fun `manual draft keeps its already-consumed app milestone and recreation never invents an average`() = runBlocking {
        val analytics = ProposalRecordingAnalytics()
        val state = personalizedState().copy(
            path = FirstPromisePath.Manual,
            draft = personalizedState().draft?.copy(source = FirstPromiseSource.Manual),
            trackedMilestones = setOf(FirstPromiseMilestone.AppSelection),
        )
        val store = firstPromiseStore(state)
        val viewModel = viewModel(store, analytics, rehydrateAverage = { null })
        val navigation = async { viewModel.container.sideEffectFlow.first() }

        viewModel.onStepViewed()
        delay(50)
        assertNull(viewModel.container.stateFlow.value.averageDailyMinutes)
        assertEquals(4, viewModel.container.stateFlow.value.usageCoverageDays)
        assertEquals(3, viewModel.container.stateFlow.value.eventCoverageDays)
        viewModel.startFirstPromise()

        assertEquals(PromiseProposalSideEffect.NavigateAccessibility, navigation.await())
        assertEquals(0, analytics.selectCompleteCount)
        assertEquals(0, analytics.appSelectionCount)
        assertEquals("Video", store.readState().draft?.appLabel)
    }

    private fun viewModel(
        store: com.uiery.keep.datastore.FirstPromiseDraftStore,
        analytics: ProposalRecordingAnalytics = ProposalRecordingAnalytics(),
        dispatcher: FirstPromiseOnboardingAnalyticsDispatcher =
            FirstPromiseOnboardingAnalyticsDispatcher(store, analytics),
        rehydrateAverage: suspend (FirstPromiseDraft) -> Long? = { null },
    ) = PromiseProposalViewModel(
        draftStore = store,
        analytics = analytics,
        onboardingAnalyticsDispatcher = dispatcher,
        rehydrateAverage = rehydrateAverage,
        dispatcher = Dispatchers.Unconfined,
    )

    private fun personalizedState(): FirstPromiseOnboardingState {
        val draft = FirstPromiseDraft(
            draftId = "draft-1",
            goal = FirstPromiseGoal.Sleep,
            packageName = "com.example.video",
            appLabel = "Video",
            startMinutes = 23 * 60,
            repeatDays = (1..7).toSet(),
            source = FirstPromiseSource.Personalized,
        )
        return FirstPromiseOnboardingState(
            assignment = OnboardingVariant.PromiseCoachV1,
            assignmentVersion = OnboardingAssignmentVersion.V1,
            phase = FirstPromisePhase.DraftReady,
            path = FirstPromisePath.Personalized,
            goal = FirstPromiseGoal.Sleep,
            draft = draft,
            recommendationReasonRef = RecommendationReasonRef(
                patternType = UsagePatternType.Night,
                usageCoverageDays = 4,
                eventCoverageDays = 3,
                isGoalDefault = false,
                selectedStartMinutes = draft.startMinutes,
            ),
        )
    }
}

private class ProposalRecordingAnalytics : FirstPromiseRecordingAnalytics() {
    val edits = mutableListOf<PromiseEditField>()
    var recommendationShownCount = 0
    var proposalViewCount = 0
    var proposalCompleteCount = 0
    var selectCompleteCount = 0
    var appSelectionCount = 0

    override fun trackPromiseRecommendationEdited(fieldName: PromiseEditField) {
        edits += fieldName
    }

    override fun trackPromiseRecommendationShown(
        goalType: FirstPromiseGoal,
        patternType: UsagePatternType,
        source: FirstPromiseSource,
    ) {
        recommendationShownCount++
    }

    override fun trackOnboardingStepView(stepName: String) {
        super.trackOnboardingStepView(stepName)
        if (stepName == "promise_proposal") proposalViewCount++
    }

    override fun trackOnboardingStepComplete(stepName: String) {
        super.trackOnboardingStepComplete(stepName)
        if (stepName == "promise_proposal") proposalCompleteCount++
        if (stepName == "select_app") selectCompleteCount++
    }

    override fun trackAppSelectionCompleted(selectedAppCount: Int, isOnboarding: Boolean) {
        super.trackAppSelectionCompleted(selectedAppCount, isOnboarding)
        if (selectedAppCount == 1 && isOnboarding) appSelectionCount++
    }
}
