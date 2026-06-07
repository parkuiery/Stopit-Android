package com.uiery.keep.analytics.acquisition

import com.uiery.keep.analytics.KeepAnalytics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallReferrerAttributionReporterTest {

    @Test
    fun firstLaunchLookupTracksPrivacySafeAttributionOnceAndMarksCompleted() = runBlocking {
        val store = FakeInstallReferrerAttributionStore(alreadyChecked = false)
        val lookup = FakeInstallReferrerLookup(
            result = InstallReferrerLookupResult(
                rawReferrer = "utm_source=discord&utm_medium=social&utm_campaign=aso_baseline&search_term=private@example.com",
                status = InstallReferrerLookupStatus.SUCCESS,
                latencyMillis = 640,
            )
        )
        val analytics = RecordingAnalytics()
        val reporter = InstallReferrerAttributionReporter(
            lookup = lookup,
            store = store,
            analytics = analytics,
        )

        reporter.checkOnceAfterFirstLaunch()
        reporter.checkOnceAfterFirstLaunch()

        assertEquals(1, lookup.calls)
        assertEquals(true, store.completed)
        assertEquals(1, analytics.attributions.size)
        val attribution = analytics.attributions.single()
        assertEquals(ReferrerStatus.SUCCESS, attribution.referrerStatus)
        assertEquals(UtmSourceType.DISCORD, attribution.utmSourceType)
        assertEquals(UtmMediumType.SOCIAL, attribution.utmMediumType)
        assertEquals(CampaignBucket.ASO_BASELINE, attribution.campaignBucket)
        assertEquals(LookupLatencyBucket.MS_500_999, attribution.lookupLatencyBucket)
        assertEquals(false, attribution.toAnalyticsParams().values.any { it.contains("private@example.com") })
    }

    @Test
    fun lookupFailuresStillTrackTerminalStatusAndMarkCompleted() = runBlocking {
        val store = FakeInstallReferrerAttributionStore(alreadyChecked = false)
        val analytics = RecordingAnalytics()
        val reporter = InstallReferrerAttributionReporter(
            lookup = FakeInstallReferrerLookup(
                result = InstallReferrerLookupResult(
                    rawReferrer = null,
                    status = InstallReferrerLookupStatus.ERROR,
                    latencyMillis = 2_500,
                )
            ),
            store = store,
            analytics = analytics,
        )

        reporter.checkOnceAfterFirstLaunch()

        assertEquals(true, store.completed)
        assertEquals(ReferrerStatus.ERROR, analytics.attributions.single().referrerStatus)
        assertEquals(UtmSourceType.NONE, analytics.attributions.single().utmSourceType)
        assertEquals(LookupLatencyBucket.MS_2000_PLUS, analytics.attributions.single().lookupLatencyBucket)
    }

    @Test
    fun alreadyCompletedLookupDoesNotTouchProviderOrAnalytics() = runBlocking {
        val store = FakeInstallReferrerAttributionStore(alreadyChecked = true)
        val lookup = FakeInstallReferrerLookup(
            result = InstallReferrerLookupResult(
                rawReferrer = "utm_source=discord&utm_medium=social&utm_campaign=aso_baseline",
                status = InstallReferrerLookupStatus.SUCCESS,
                latencyMillis = 100,
            )
        )
        val analytics = RecordingAnalytics()
        val reporter = InstallReferrerAttributionReporter(
            lookup = lookup,
            store = store,
            analytics = analytics,
        )

        reporter.checkOnceAfterFirstLaunch()

        assertEquals(0, lookup.calls)
        assertEquals(0, analytics.attributions.size)
    }

    private class FakeInstallReferrerLookup(
        private val result: InstallReferrerLookupResult,
    ) : InstallReferrerLookup {
        var calls = 0
            private set

        override suspend fun lookup(): InstallReferrerLookupResult {
            calls += 1
            return result
        }
    }

    private class FakeInstallReferrerAttributionStore(
        alreadyChecked: Boolean,
    ) : InstallReferrerAttributionStore {
        var completed = alreadyChecked
            private set

        override suspend fun hasCheckedInstallReferrerAttribution(): Boolean = completed

        override suspend fun markInstallReferrerAttributionChecked() {
            completed = true
        }
    }

    private class RecordingAnalytics : KeepAnalytics {
        val attributions = mutableListOf<AcquisitionAttribution>()

        override fun logEvent(name: String, params: Map<String, Any?>) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
        override fun trackFirstOpen() = Unit
        override fun trackOnboardingStepView(stepName: String) = Unit
        override fun trackOnboardingStepComplete(stepName: String) = Unit
        override fun trackPermissionOutcome(permissionName: String, outcome: String, stepName: String?) = Unit
        override fun trackFirstLockConfigured(source: String, selectedAppCount: Int?) = Unit
        override fun trackLockSessionStart(source: String, isRoutine: Boolean?) = Unit
        override fun trackLockSessionEnd(source: String, endReason: String, isRoutine: Boolean?) = Unit
        override fun trackEmergencyUnlockUsed(source: String, unlockCountRemaining: Int?) = Unit
        override fun trackInstallReferrerAttributionChecked(attribution: AcquisitionAttribution) {
            attributions += attribution
        }
    }
}
