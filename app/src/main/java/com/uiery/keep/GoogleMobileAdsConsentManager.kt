package com.uiery.keep

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

internal class GoogleMobileAdsConsentManager private constructor(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(
        activity: Activity,
        onComplete: (FormError?) -> Unit,
    ) {
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                    onComplete,
                )
            },
            onComplete,
        )
    }

    fun showPrivacyOptionsForm(
        activity: Activity,
        onComplete: (FormError?) -> Unit,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onComplete)
    }

    companion object {
        @Volatile
        private var instance: GoogleMobileAdsConsentManager? = null

        fun getInstance(context: Context): GoogleMobileAdsConsentManager =
            instance ?: synchronized(this) {
                instance ?: GoogleMobileAdsConsentManager(context.applicationContext)
                    .also { instance = it }
            }
    }
}
