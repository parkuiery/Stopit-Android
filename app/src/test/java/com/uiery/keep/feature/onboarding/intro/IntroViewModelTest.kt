package com.uiery.keep.feature.onboarding.intro

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.analytics.OnboardingStepName
import com.uiery.keep.datastore.FirstPromiseDraftStore
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.firstpromise.FirstPromiseMilestone
import com.uiery.keep.domain.firstpromise.FirstPromiseOnboardingState
import com.uiery.keep.domain.firstpromise.OnboardingAssignmentVersion
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.feature.onboarding.FirstPromiseAnalyticsCall
import com.uiery.keep.feature.onboarding.FirstPromiseRecordingAnalytics
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroViewModelTest {
    @Test
    fun immediateContinueWaitsForDurableControlExposureBeforeCompletingAndNavigating() = runBlocking {
        val dataStore = ControllableDataStore(controlPreferences())
        val analytics = FirstPromiseRecordingAnalytics()
        val draftStore = FirstPromiseDraftStore(dataStore)
        val viewModel = IntroViewModel(analytics, draftStore)
        val navigation = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.container.sideEffectFlow.first()
        }

        viewModel.onStepViewed()
        dataStore.updateStarted.await()
        viewModel.onContinue()
        viewModel.onContinue()
        delay(20)

        assertFalse(FirstPromiseMilestone.Exposure in draftStore.readState().trackedMilestones)
        assertEquals(
            0,
            analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO) },
        )
        assertFalse(navigation.isCompleted)

        dataStore.allowUpdate.complete(Unit)
        assertEquals(IntroSideEffect.NavigatePermissionSetting, navigation.await())

        assertTrue(FirstPromiseMilestone.Exposure in draftStore.readState().trackedMilestones)
        assertEquals(1, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
        assertEquals(
            1,
            analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO) },
        )
        assertTrue(
            analytics.calls.indexOfFirst { it is FirstPromiseAnalyticsCall.Exposure } <
                analytics.calls.indexOfFirst {
                    it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO)
                },
        )
    }

    @Test
    fun failedOrCancelledExposureCanRetryAndCompleteExactlyOnce() = runBlocking {
        listOf(
            IOException("write failed"),
            CancellationException("write cancelled"),
        ).forEach { failure ->
            val analytics = FirstPromiseRecordingAnalytics()
            val dataStore = RetryableDataStore(controlPreferences(), failure)
            val draftStore = FirstPromiseDraftStore(dataStore)
            val viewModel = IntroViewModel(
                analytics,
                draftStore,
            )
            val navigation = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.container.sideEffectFlow.first()
            }

            viewModel.onStepViewed()
            dataStore.updateStarted.await()
            viewModel.onContinue()
            viewModel.onContinue()
            delay(20)

            assertFalse(navigation.isCompleted)
            assertEquals(
                0,
                analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO) },
            )

            dataStore.allowFailure.complete(Unit)
            dataStore.firstFailureDelivered.await()
            delay(50)

            assertEquals(0, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
            assertEquals(
                0,
                analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO) },
            )
            assertFalse(navigation.isCompleted)

            viewModel.onStepViewed()
            withTimeout(1_000) {
                while (FirstPromiseMilestone.Exposure !in draftStore.readState().trackedMilestones) {
                    delay(10)
                }
            }
            viewModel.onContinue()
            viewModel.onContinue()

            assertEquals(IntroSideEffect.NavigatePermissionSetting, navigation.await())
            assertEquals(1, analytics.calls.count { it is FirstPromiseAnalyticsCall.Exposure })
            assertEquals(
                1,
                analytics.calls.count { it == FirstPromiseAnalyticsCall.StepComplete(OnboardingStepName.INTRO) },
            )
        }
    }
}

private class ControllableDataStore(initial: Preferences) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    val updateStarted = CompletableDeferred<Unit>()
    val allowUpdate = CompletableDeferred<Unit>()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        updateStarted.complete(Unit)
        allowUpdate.await()
        return transform(state.value).also { state.value = it }
    }
}

private class RetryableDataStore(
    initial: Preferences,
    private val firstFailure: Throwable,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private var updateCount = 0
    override val data: Flow<Preferences> = state
    val updateStarted = CompletableDeferred<Unit>()
    val allowFailure = CompletableDeferred<Unit>()
    val firstFailureDelivered = CompletableDeferred<Unit>()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (++updateCount == 1) {
            updateStarted.complete(Unit)
            allowFailure.await()
            firstFailureDelivered.complete(Unit)
            throw firstFailure
        }
        return transform(state.value).also { state.value = it }
    }
}

private fun controlPreferences(): Preferences = mutablePreferencesOf(
    PreferencesKey.FIRST_PROMISE_ONBOARDING_STATE to Json.encodeToString(
        FirstPromiseOnboardingState(
            assignment = OnboardingVariant.Control,
            assignmentVersion = OnboardingAssignmentVersion.V1,
        ),
    ),
)
