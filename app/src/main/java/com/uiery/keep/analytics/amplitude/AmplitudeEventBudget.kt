package com.uiery.keep.analytics.amplitude

import android.content.Context
import java.util.Calendar

/**
 * Hard per-device monthly cap on events sent to Amplitude, so ingestion can never exceed
 * the free tier and never triggers billing.
 *
 * Guarantee: with a per-device cap `C` and at most `M` monthly tracked users, total
 * ingested events ≤ `C × M`. Keep `C × M` below Amplitude's 2,000,000 events/month free
 * cap. With the default cap 180 and the free-tier MTU ceiling of 10,000:
 * `180 × 10,000 = 1,800,000 < 2,000,000`. Tune `AMPLITUDE_MONTHLY_EVENT_CAP` upward when
 * the active user base is well below 10,000 to use more of the free budget.
 */
internal fun interface AmplitudeEventBudget {
    /** Consumes one unit and returns true while budget remains this month; false when exhausted. */
    fun tryConsume(): Boolean
}

/** Pure monthly-budget decision, extracted so the cap/reset logic is unit-testable. */
internal object EventBudgetPolicy {
    /**
     * @return the new stored count when the event is allowed, or null when the monthly cap
     * is already reached. A period change resets the running count to zero.
     */
    fun nextCount(
        storedPeriod: String?,
        storedCount: Int,
        currentPeriod: String,
        cap: Int,
    ): Int? {
        val count = if (storedPeriod == currentPeriod) storedCount else 0
        return if (count >= cap) null else count + 1
    }
}

/** [AmplitudeEventBudget] persisted per install via [android.content.SharedPreferences]. */
internal class SharedPreferencesAmplitudeEventBudget(
    context: Context,
    private val maxMonthlyEvents: Int,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : AmplitudeEventBudget {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun tryConsume(): Boolean {
        val period = currentPeriod()
        val next = EventBudgetPolicy.nextCount(
            storedPeriod = prefs.getString(KEY_PERIOD, null),
            storedCount = prefs.getInt(KEY_COUNT, 0),
            currentPeriod = period,
            cap = maxMonthlyEvents,
        ) ?: return false
        prefs.edit().putString(KEY_PERIOD, period).putInt(KEY_COUNT, next).apply()
        return true
    }

    private fun currentPeriod(): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = clockMillis()
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
    }

    companion object {
        private const val PREFS_NAME = "amplitude_event_budget"
        private const val KEY_PERIOD = "period"
        private const val KEY_COUNT = "count"
    }
}
