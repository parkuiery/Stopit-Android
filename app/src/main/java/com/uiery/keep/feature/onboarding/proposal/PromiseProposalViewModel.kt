package com.uiery.keep.feature.onboarding.proposal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.FirstPromiseOnboardingAnalyticsDispatcher
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.data.usageinsight.OnboardingUsageProfileRepository
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.domain.firstpromise.FirstPromiseDraft
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseStateMutation
import com.uiery.keep.domain.firstpromise.PromiseEditField
import com.uiery.keep.domain.firstpromise.UsagePatternType
import com.uiery.keep.domain.firstpromise.FirstPromiseRecommendationPolicy
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.feature.onboarding.usageanalysis.FirstPromiseAnalysisTransientHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container

data class PromiseProposalUiState(
    val appLabel: String = "",
    val averageDailyMinutes: Long? = null,
    val patternType: UsagePatternType = UsagePatternType.Manual,
    val usageCoverageDays: Int = 0,
    val eventCoverageDays: Int = 0,
    val isGoalDefault: Boolean = false,
    val startMinutes: Int = 0,
    val repeatDays: Set<Int> = emptySet(),
    val visiblePicker: ProposalPicker = ProposalPicker.None,
    val isLoading: Boolean = true,
) {
    val canStart: Boolean
        get() = appLabel.isNotBlank() && startMinutes in 0..1439 && repeatDays.isNotEmpty()
    val isPickerVisible: Boolean
        get() = visiblePicker != ProposalPicker.None
}

enum class ProposalPicker { None, App, StartTime, RepeatDays }

sealed interface PromiseProposalSideEffect {
    data object NavigateAccessibility : PromiseProposalSideEffect
}

@HiltViewModel
class PromiseProposalViewModel internal constructor(
    private val draftStore: FirstPromiseDraftStore,
    private val analytics: KeepAnalytics,
    private val onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
    private val rehydrateAverage: suspend (FirstPromiseDraft) -> Long?,
    private val dispatcher: CoroutineDispatcher,
) : ViewModel(), ContainerHost<PromiseProposalUiState, PromiseProposalSideEffect> {
    @Inject constructor(
        draftStore: FirstPromiseDraftStore,
        analytics: KeepAnalytics,
        onboardingAnalyticsDispatcher: FirstPromiseOnboardingAnalyticsDispatcher,
        repository: OnboardingUsageProfileRepository,
    ) : this(
        draftStore = draftStore,
        analytics = analytics,
        onboardingAnalyticsDispatcher = onboardingAnalyticsDispatcher,
        rehydrateAverage = { draft ->
            when (
                val result = repository.profile(
                    today = LocalDate.now(),
                    zoneId = ZoneId.systemDefault(),
                    goalDefaultStartMinutes = FirstPromiseRecommendationPolicy.defaultStartMinutes(draft.goal),
                )
            ) {
                is OnboardingUsageProfileResult.Ready ->
                    result.profile.takeIf { it.packageName == draft.packageName }?.averageDailyMinutes
                is OnboardingUsageProfileResult.Insufficient -> null
            }
        },
        dispatcher = Dispatchers.IO,
    )

    override val container: Container<PromiseProposalUiState, PromiseProposalSideEffect> =
        container(PromiseProposalUiState())
    private val commandMutex = Mutex()

    fun onStepViewed() {
        viewModelScope.launch(dispatcher) {
            commandMutex.withLock {
                analytics.logScreenView(KeepAnalyticsScreen.ONBOARDING_PROMISE_PROPOSAL)
                draftStore.markPromiseProposalViewed()
                onboardingAnalyticsDispatcher.drain()
                if (!container.stateFlow.value.isLoading) return@withLock
                val durableState = draftStore.readState()
                val draft = durableState.draft ?: return@withLock
                val reason = durableState.recommendationReasonRef ?: return@withLock
                val transientAverage = FirstPromiseAnalysisTransientHolder.consume(draft.draftId)
                val average = transientAverage ?: runCatching { rehydrateAverage(draft) }.getOrNull()
                intent {
                    reduce {
                        state.copy(
                            appLabel = draft.appLabel,
                            averageDailyMinutes = average,
                            patternType = reason.patternType,
                            usageCoverageDays = reason.usageCoverageDays,
                            eventCoverageDays = reason.eventCoverageDays,
                            isGoalDefault = reason.isGoalDefault,
                            startMinutes = draft.startMinutes,
                            repeatDays = draft.repeatDays,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    fun showPicker(picker: ProposalPicker) = intent {
        reduce { state.copy(visiblePicker = picker) }
    }

    fun hidePicker() = intent {
        reduce { state.copy(visiblePicker = ProposalPicker.None) }
    }

    fun changeApp(packageName: String, appLabel: String) {
        if (packageName.isBlank() || appLabel.isBlank()) return
        editDraft(PromiseEditField.App) { it.copy(packageName = packageName, appLabel = appLabel) }
    }

    fun changeStartMinutes(startMinutes: Int) {
        if (startMinutes !in 0..1439) return
        editDraft(PromiseEditField.StartTime) { it.copy(startMinutes = startMinutes) }
    }

    fun toggleRepeatDay(day: Int) {
        if (day !in 1..7) return
        editDraft(PromiseEditField.RepeatDays) { draft ->
            val next = if (day in draft.repeatDays) draft.repeatDays - day else draft.repeatDays + day
            if (next.isEmpty()) draft else draft.copy(repeatDays = next)
        }
    }

    fun startFirstPromise() {
        viewModelScope.launch(dispatcher) {
            commandMutex.withLock {
                if (draftStore.startFirstPromise() !is FirstPromiseStateMutation.Changed) return@withLock
                onboardingAnalyticsDispatcher.drain()
                intent { postSideEffect(PromiseProposalSideEffect.NavigateAccessibility) }
            }
        }
    }

    private fun editDraft(
        field: PromiseEditField,
        transform: (FirstPromiseDraft) -> FirstPromiseDraft,
    ) {
        viewModelScope.launch(dispatcher) {
            commandMutex.withLock {
                val durableState = draftStore.readState()
                val draft = durableState.draft ?: return@withLock
                val reason = durableState.recommendationReasonRef ?: return@withLock
                val edited = transform(draft)
                if (edited == draft) return@withLock
                val editedReason = reason.copy(selectedStartMinutes = edited.startMinutes)
                if (draftStore.editDraft(edited, editedReason) !is FirstPromiseStateMutation.Changed) {
                    return@withLock
                }
                analytics.trackPromiseRecommendationEdited(field)
                intent {
                    reduce {
                        state.copy(
                            appLabel = edited.appLabel,
                            startMinutes = edited.startMinutes,
                            repeatDays = edited.repeatDays,
                            visiblePicker = ProposalPicker.None,
                        )
                    }
                }
            }
        }
    }
}
