package com.uiery.keep

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.google.android.gms.ads.MobileAds
import com.uiery.keep.util.isTestMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KeepApplication: Application() {

    override fun onCreate() {
        super.onCreate()
    }
}