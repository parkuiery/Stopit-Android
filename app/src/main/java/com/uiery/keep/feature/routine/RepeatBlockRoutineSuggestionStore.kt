package com.uiery.keep.feature.routine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.datastore.PreferencesKey
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first

private const val DISMISSED_SUGGESTION_FIELD_COUNT = 4
private const val MAX_DISMISSED_SUGGESTIONS = 20

/**
 * Local-only persistence for repeat-block routine suggestion decisions.
 *
 * The stored value intentionally keeps only privacy-safe buckets plus the dismissal timestamp.
 * Raw app names/packages from [RepeatBlockRoutineSuggestion.prefillPackages] never enter this store.
 */
class RepeatBlockRoutineSuggestionStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun readDismissedSuggestions(): List<RepeatBlockDismissedSuggestion> =
        dataStore.data.first()[PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS]
            .orEmpty()
            .mapNotNull(::decodeDismissedSuggestion)
            .sortedByDescending { it.dismissedAt }

    suspend fun recordDismissed(
        suggestion: RepeatBlockRoutineSuggestion,
        dismissedAt: LocalDateTime,
    ) {
        val next = RepeatBlockDismissedSuggestion(
            timeBucket = suggestion.timeBucket,
            dayType = suggestion.dayType,
            categoryBucket = suggestion.categoryBucket,
            dismissedAt = dismissedAt,
        )
        dataStore.edit { preferences ->
            val retained = preferences[PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS]
                .orEmpty()
                .mapNotNull(::decodeDismissedSuggestion)
                .filterNot { existing -> existing.sameSuggestionKeyAs(next) }
                .plus(next)
                .sortedByDescending { it.dismissedAt }
                .take(MAX_DISMISSED_SUGGESTIONS)
                .map(::encodeDismissedSuggestion)
                .toSet()

            if (retained.isEmpty()) {
                preferences.remove(PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS)
            } else {
                preferences[PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS] = retained
            }
        }
    }
}

private fun RepeatBlockDismissedSuggestion.sameSuggestionKeyAs(other: RepeatBlockDismissedSuggestion): Boolean =
    timeBucket == other.timeBucket &&
        dayType == other.dayType &&
        categoryBucket == other.categoryBucket

private fun encodeDismissedSuggestion(dismissed: RepeatBlockDismissedSuggestion): String = listOf(
    dismissed.timeBucket.analyticsValue,
    dismissed.dayType.analyticsValue,
    dismissed.categoryBucket.analyticsValue,
    dismissed.dismissedAt.toString(),
).joinToString(separator = "|")

private fun decodeDismissedSuggestion(value: String): RepeatBlockDismissedSuggestion? {
    val parts = value.split("|")
    if (parts.size != DISMISSED_SUGGESTION_FIELD_COUNT) return null
    return RepeatBlockDismissedSuggestion(
        timeBucket = repeatBlockTimeBucketFromAnalyticsValue(parts[0]) ?: return null,
        dayType = repeatBlockDayTypeFromAnalyticsValue(parts[1]) ?: return null,
        categoryBucket = repeatBlockCategoryBucketFromAnalyticsValue(parts[2]) ?: return null,
        dismissedAt = runCatching { LocalDateTime.parse(parts[3]) }.getOrNull() ?: return null,
    )
}

private fun repeatBlockTimeBucketFromAnalyticsValue(value: String): RepeatBlockTimeBucket? =
    RepeatBlockTimeBucket.entries.firstOrNull { it.analyticsValue == value }

private fun repeatBlockDayTypeFromAnalyticsValue(value: String): RepeatBlockDayType? =
    RepeatBlockDayType.entries.firstOrNull { it.analyticsValue == value }

private fun repeatBlockCategoryBucketFromAnalyticsValue(value: String): RepeatBlockCategoryBucket? =
    RepeatBlockCategoryBucket.entries.firstOrNull { it.analyticsValue == value }
