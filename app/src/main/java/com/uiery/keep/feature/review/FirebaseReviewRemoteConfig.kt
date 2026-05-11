package com.uiery.keep.feature.review

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY = "in_app_review_enabled"
private const val FETCH_TIMEOUT_SECONDS = 5L

@Singleton
class FirebaseReviewRemoteConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : ReviewRemoteConfig {

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SECONDS)
                .build()
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY to true))
        remoteConfig.fetchAndActivate()
    }

    override fun isEnabled(): Boolean = remoteConfig.getBoolean(KEY)
}
