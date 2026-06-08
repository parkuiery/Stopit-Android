package com.uiery.keep.feature.routine

import com.uiery.keep.feature.review.FakeDataStore
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatBlockRoutineSuggestionStoreTest {
    @Test
    fun dismissedSuggestionPersistsAsPolicyInputAcrossStoreInstances() = runBlocking {
        val dataStore = FakeDataStore()
        val store = RepeatBlockRoutineSuggestionStore(dataStore)
        val dismissedAt = LocalDateTime.of(2026, 6, 6, 10, 30)

        store.recordDismissed(
            suggestion = RepeatBlockRoutineSuggestion(
                timeBucket = RepeatBlockTimeBucket.Night,
                dayType = RepeatBlockDayType.Weekday,
                categoryBucket = RepeatBlockCategoryBucket.Social,
                repeatCountBucket = RepeatBlockCountBucket.ThreeToFive,
                routineCoverageState = RoutineCoverageState.NotCovered,
                reason = RepeatBlockSuggestionReason.RepeatBlockTimeBucket,
                prefillPackages = listOf("com.instagram.android"),
                prefillStartTime = kotlinx.datetime.LocalTime(22, 0),
                prefillEndTime = kotlinx.datetime.LocalTime(0, 0),
            ),
            dismissedAt = dismissedAt,
        )

        val restored = RepeatBlockRoutineSuggestionStore(dataStore).readDismissedSuggestions()

        assertEquals(
            listOf(
                RepeatBlockDismissedSuggestion(
                    timeBucket = RepeatBlockTimeBucket.Night,
                    dayType = RepeatBlockDayType.Weekday,
                    categoryBucket = RepeatBlockCategoryBucket.Social,
                    dismissedAt = dismissedAt,
                ),
            ),
            restored,
        )
    }

    @Test
    fun dismissedSuggestionStoreDropsMalformedRowsInsteadOfBlockingSuggestions() = runBlocking {
        val dataStore = FakeDataStore.withPrefs {
            this[com.uiery.keep.datastore.PreferencesKey.REPEAT_BLOCK_DISMISSED_SUGGESTIONS] = setOf(
                "night|weekday|social|2026-06-06T10:30:00",
                "raw.com.instagram.android|weekday|social|2026-06-06T10:30:00",
                "night|weekday|social|not-a-date",
            )
        }

        val dismissed = RepeatBlockRoutineSuggestionStore(dataStore).readDismissedSuggestions()

        assertEquals(
            listOf(
                RepeatBlockDismissedSuggestion(
                    timeBucket = RepeatBlockTimeBucket.Night,
                    dayType = RepeatBlockDayType.Weekday,
                    categoryBucket = RepeatBlockCategoryBucket.Social,
                    dismissedAt = LocalDateTime.of(2026, 6, 6, 10, 30),
                ),
            ),
            dismissed,
        )
    }
}
