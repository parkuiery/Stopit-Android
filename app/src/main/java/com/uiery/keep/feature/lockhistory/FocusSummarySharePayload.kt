package com.uiery.keep.feature.lockhistory

private const val DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.uiery.keep"

data class FocusSummarySharePayload(
    val text: String,
    val periodType: String,
    val sessionCountBucket: String,
    val durationMinutesBucket: String,
)

fun buildFocusSummarySharePayload(
    periodType: PeriodType,
    sessionCount: Int,
    totalDurationMillis: Long,
    playStoreUrl: String = DEFAULT_PLAY_STORE_URL,
): FocusSummarySharePayload? {
    if (periodType != PeriodType.WEEK || sessionCount <= 0 || totalDurationMillis <= 0L) return null

    val durationMinutes = (totalDurationMillis / 60_000L).coerceAtLeast(1L)
    val durationText = formatFocusSummaryDuration(durationMinutes)
    return FocusSummarySharePayload(
        text = "이번 주 스탑잇으로 ${sessionCount}번, 총 $durationText 집중을 지켰어요.\n" +
            "나도 집중이 필요할 때 앱 사용을 잠깐 멈춰요.\n" +
            playStoreUrl,
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

private fun formatFocusSummaryDuration(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간"
        else -> "${minutes}분"
    }
}
