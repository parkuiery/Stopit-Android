package com.uiery.keep.feature.onboarding.intro

import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    private val analyticsService: FirebaseAnalytics
) : ViewModel() {
    fun onBoardingAnalytics() {
        analyticsService.logEvent(name = "onboarding_intro_started") {}
    }
}
