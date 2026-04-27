package com.uiery.keep.feature.onboarding.intro

import androidx.lifecycle.ViewModel
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.OnboardingStepName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val analytics: KeepAnalytics,
) : ViewModel() {
    fun onStepViewed() {
        analytics.trackOnboardingStepView(OnboardingStepName.INTRO)
    }

    fun onContinue() {
        analytics.trackOnboardingStepComplete(OnboardingStepName.INTRO)
    }
}
