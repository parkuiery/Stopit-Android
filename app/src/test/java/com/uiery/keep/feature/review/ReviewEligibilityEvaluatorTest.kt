package com.uiery.keep.feature.review

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.uiery.keep.datastore.PreferencesKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewEligibilityEvaluatorTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val baselineInstant: Instant = Instant.parse("2026-05-11T14:30:00Z")
    private val baselineNowMs: Long = baselineInstant.toEpochMilli()

    private fun clockAt(instant: Instant): Clock = Clock.fixed(instant, zone)

    private fun baselinePrefs() = mutablePreferencesOf().apply {
        set(PreferencesKey.SUCCESSFUL_SESSION_COUNT, 5)
        set(PreferencesKey.LAST_BACKGROUNDED_AT_MS, baselineNowMs - 5_000)
    }

    private fun newEvaluator(
        prefs: androidx.datastore.preferences.core.Preferences = baselinePrefs(),
        clock: Clock = clockAt(baselineInstant),
        rcEnabled: Boolean = true,
        accessibilityEnabled: Boolean = true,
        emergencyCount: Int = 0,
        recentSuccess: Int = 1,
        isDebug: Boolean = false,
        flavor: String = "prod",
    ) = ReviewEligibilityEvaluator(
        dataStore = FakeDataStore(prefs),
        remoteConfig = FakeReviewRemoteConfig(rcEnabled),
        accessibilityChecker = FakeAccessibilityChecker(accessibilityEnabled),
        emergencyUnlockDao = FakeEmergencyUnlockDao(emergencyCount),
        lockHistoryDao = FakeLockHistoryDao(recentSuccess),
        clock = clock,
        buildConfig = ReviewBuildConfig(isDebug = isDebug, flavor = flavor),
    )

    private fun evaluate(evaluator: ReviewEligibilityEvaluator): ReviewEligibilityDecision = runBlocking {
        evaluator.evaluate(nowMs = baselineNowMs, durationMillis = 60_000L, isRoutine = false)
    }

    @Test
    fun baselineIsEligible() {
        assertEquals(ReviewEligibilityDecision.Eligible, evaluate(newEvaluator()))
    }

    @Test
    fun killSwitchDisabledShortCircuits() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.KillSwitch),
            evaluate(newEvaluator(rcEnabled = false)),
        )
    }

    @Test
    fun debugBuildIsIneligible() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.Debug),
            evaluate(newEvaluator(isDebug = true)),
        )
    }

    @Test
    fun devFlavorIsIneligible() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.DevFlavor),
            evaluate(newEvaluator(flavor = "dev")),
        )
    }

    @Test
    fun accessibilityOffIsIneligible() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.AccessibilityOff),
            evaluate(newEvaluator(accessibilityEnabled = false)),
        )
    }

    @Test
    fun quietHoursAreIneligible() {
        // 16:30 UTC = 01:30 KST, inside quiet hours [01:00, 06:00)
        val quietInstant = Instant.parse("2026-05-11T16:30:00Z")
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.QuietHours),
            runBlocking {
                newEvaluator(clock = clockAt(quietInstant))
                    .evaluate(nowMs = quietInstant.toEpochMilli(), durationMillis = 60_000L, isRoutine = false)
            },
        )
    }

    @Test
    fun belowSessionThresholdIsIneligible() {
        val prefs = baselinePrefs().apply { set(PreferencesKey.SUCCESSFUL_SESSION_COUNT, 2) }
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.BelowSessionThreshold),
            evaluate(newEvaluator(prefs = prefs)),
        )
    }

    @Test
    fun withinCooldownIsIneligible() {
        val prefs = baselinePrefs().apply {
            set(PreferencesKey.LAST_REVIEW_PROMPT_AT_MS, baselineNowMs - (89L * 24 * 60 * 60 * 1000))
        }
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.WithinCooldown),
            evaluate(newEvaluator(prefs = prefs)),
        )
    }

    @Test
    fun cooldownTakesPrecedenceOverSameDayCheck() {
        val prefs = baselinePrefs().apply {
            set(PreferencesKey.LAST_REVIEW_PROMPT_AT_MS, baselineNowMs)
        }
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.WithinCooldown),
            evaluate(newEvaluator(prefs = prefs)),
        )
    }

    @Test
    fun noBackgroundingObservedIsIneligible() {
        val prefs = mutablePreferencesOf().apply {
            set(PreferencesKey.SUCCESSFUL_SESSION_COUNT, 5)
            // LAST_BACKGROUNDED_AT_MS deliberately missing
        }
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.NoBackgroundingObserved),
            evaluate(newEvaluator(prefs = prefs)),
        )
    }

    @Test
    fun withinSameSessionIsIneligible() {
        val prefs = baselinePrefs().apply {
            set(PreferencesKey.LAST_BACKGROUNDED_AT_MS, baselineNowMs - 500)
        }
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.WithinSameSession),
            evaluate(newEvaluator(prefs = prefs)),
        )
    }

    @Test
    fun recentEmergencyUnlockIsIneligible() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.RecentEmergencyUnlock),
            evaluate(newEvaluator(emergencyCount = 2)),
        )
    }

    @Test
    fun noRecentSuccessIsIneligible() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.NoRecentSuccess),
            evaluate(newEvaluator(recentSuccess = 0)),
        )
    }

    @Test
    fun evaluateLiveReturnsEligibleByDefault() {
        assertEquals(ReviewEligibilityDecision.Eligible, newEvaluator().evaluateLive())
    }

    @Test
    fun evaluateLiveRespectsKillSwitch() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.KillSwitch),
            newEvaluator(rcEnabled = false).evaluateLive(),
        )
    }

    @Test
    fun evaluateLiveRespectsAccessibility() {
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.AccessibilityOff),
            newEvaluator(accessibilityEnabled = false).evaluateLive(),
        )
    }

    @Test
    fun evaluateLiveRespectsQuietHours() {
        // 17:30 UTC = 02:30 KST, inside quiet hours
        val quietInstant = Instant.parse("2026-05-11T17:30:00Z")
        assertEquals(
            ReviewEligibilityDecision.Ineligible(SkipReason.QuietHours),
            newEvaluator(clock = clockAt(quietInstant)).evaluateLive(),
        )
    }
}
