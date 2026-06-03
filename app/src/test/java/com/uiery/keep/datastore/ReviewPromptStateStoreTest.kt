package com.uiery.keep.datastore

import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptStateStoreTest {

    @Test
    fun defaultSnapshotHasNoPendingPromptOrTimingMarkers() = runBlocking {
        val store = ReviewPromptStateStore(FakeDataStore())

        val snapshot = store.readState()

        assertFalse(snapshot.isPending)
        assertNull(snapshot.lastPromptAtMs)
        assertNull(snapshot.lastBackgroundedAtMs)
    }

    @Test
    fun pendingFlagIsMarkedAndClearedThroughReviewBoundary() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ReviewPromptStateStore(dataStore)

        store.markPending()
        assertTrue(store.readState().isPending)
        assertEquals(true, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])

        store.clearPending()
        assertFalse(store.readState().isPending)
        assertEquals(false, dataStore.snapshot()[PreferencesKey.REVIEW_PENDING])
    }

    @Test
    fun promptAndBackgroundTimestampsAreWrittenThroughReviewBoundary() = runBlocking {
        val dataStore = FakeDataStore()
        val store = ReviewPromptStateStore(dataStore)

        store.recordPromptShown(atMillis = 1_771_000_000_000L)
        store.recordBackgrounded(atMillis = 1_771_000_005_000L)

        val snapshot = store.readState()
        assertEquals(1_771_000_000_000L, snapshot.lastPromptAtMs)
        assertEquals(1_771_000_005_000L, snapshot.lastBackgroundedAtMs)
        assertEquals(1_771_000_000_000L, dataStore.snapshot()[PreferencesKey.LAST_REVIEW_PROMPT_AT_MS])
        assertEquals(1_771_000_005_000L, dataStore.snapshot()[PreferencesKey.LAST_BACKGROUNDED_AT_MS])
    }
}
