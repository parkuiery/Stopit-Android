package com.uiery.keep.data.firstpromise

import com.uiery.keep.feature.review.FakeDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseCreationBarrierStoreTest {
    @Test
    fun completedDraftSurvivesStoreRecreationWithoutCompletingUnknownDrafts() = runBlocking {
        val dataStore = FakeDataStore()
        FirstPromiseCreationBarrierStore(dataStore).markComplete("completed")

        val restored = FirstPromiseCreationBarrierStore(dataStore)

        assertTrue(restored.isComplete("completed"))
        assertFalse(restored.isComplete("unknown"))
    }
}
