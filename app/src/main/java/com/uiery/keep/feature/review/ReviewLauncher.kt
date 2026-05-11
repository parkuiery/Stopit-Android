package com.uiery.keep.feature.review

import android.app.Activity

sealed interface ReviewLaunchResult {
    data object Success : ReviewLaunchResult
    data class Failure(val error: String) : ReviewLaunchResult
}

interface ReviewLauncher {
    suspend fun launch(activity: Activity): ReviewLaunchResult
}
