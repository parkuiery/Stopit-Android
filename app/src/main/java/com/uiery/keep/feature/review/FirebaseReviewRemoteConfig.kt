package com.uiery.keep.feature.review

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY = "in_app_review_enabled"

@Singleton
class FirebaseReviewRemoteConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : ReviewRemoteConfig {
    override fun isEnabled(): Boolean = remoteConfig.getBoolean(KEY)
}
