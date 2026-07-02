package com.uiery.keep.feature.lockhistory

import android.content.Context
import com.uiery.keep.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uiery.keep"

data class FocusSummarySharePayload(
    val text: String,
    val periodType: String,
    val sessionCountBucket: String,
    val durationMinutesBucket: String,
)

data class FocusSummaryShareTextRequest(
    val sessionCount: Int,
    val durationMinutes: Long,
    val playStoreUrl: String,
)

interface FocusSummaryShareTextProvider {
    fun buildText(request: FocusSummaryShareTextRequest): String
}

class AndroidFocusSummaryShareTextProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : FocusSummaryShareTextProvider {
    override fun buildText(request: FocusSummaryShareTextRequest): String {
        val durationText = formatDuration(request.durationMinutes)
        return context.getString(
            R.string.focus_summary_share_payload_text,
            request.sessionCount,
            durationText,
            request.playStoreUrl,
        )
    }

    private fun formatDuration(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> context.getString(
                R.string.focus_summary_share_duration_hours_minutes,
                context.resources.getQuantityString(
                    R.plurals.focus_summary_share_duration_hour,
                    hours.toInt(),
                    hours,
                ),
                context.resources.getQuantityString(
                    R.plurals.focus_summary_share_duration_minute,
                    minutes.toInt(),
                    minutes,
                ),
            )
            hours > 0 -> context.resources.getQuantityString(
                R.plurals.focus_summary_share_duration_hour,
                hours.toInt(),
                hours,
            )
            else -> context.resources.getQuantityString(
                R.plurals.focus_summary_share_duration_minute,
                minutes.toInt(),
                minutes,
            )
        }
    }
}

fun buildFocusSummarySharePayload(
    periodType: PeriodType,
    sessionCount: Int,
    totalDurationMillis: Long,
    textProvider: FocusSummaryShareTextProvider,
    playStoreUrl: String = DEFAULT_PLAY_STORE_URL,
): FocusSummarySharePayload? {
    if (periodType != PeriodType.WEEK || sessionCount <= 0 || totalDurationMillis <= 0L) return null

    val durationMinutes = (totalDurationMillis / 60_000L).coerceAtLeast(1L)
    return FocusSummarySharePayload(
        text = textProvider.buildText(
            FocusSummaryShareTextRequest(
                sessionCount = sessionCount,
                durationMinutes = durationMinutes,
                playStoreUrl = playStoreUrl,
            ),
        ),
        periodType = "week",
        sessionCountBucket = focusSummarySessionCountBucket(sessionCount),
        durationMinutesBucket = focusSummaryDurationMinutesBucket(durationMinutes),
    )
}

fun focusSummarySessionCountBucket(sessionCount: Int): String = when {
    sessionCount <= 1 -> "1"
    sessionCount <= 3 -> "2_3"
    sessionCount <= 6 -> "4_6"
    else -> "7_plus"
}

fun focusSummaryDurationMinutesBucket(durationMinutes: Long): String = when {
    durationMinutes < 30 -> "1_29"
    durationMinutes < 60 -> "30_59"
    durationMinutes < 120 -> "60_119"
    durationMinutes < 240 -> "120_239"
    else -> "240_plus"
}
