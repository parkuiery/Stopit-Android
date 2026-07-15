package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromisePracticeStoreTest {

    @Test
    fun localDraftTokenRoundTripsAcrossStoreRecreationUntilExclusiveDeadline() = runBlocking {
        val dataStore = PracticeFakeDataStore()
        val token = FirstPromisePracticeToken(
            draftId = "local-draft-id",
            startedAtMillis = 1_000L,
            expiresAtMillis = 601_000L,
        )

        FirstPromisePracticeStore(dataStore).saveToken(token)
        val recreatedStore = FirstPromisePracticeStore(dataStore)

        assertEquals(token, recreatedStore.readActiveToken(nowMillis = 600_999L))
        assertEquals(1, dataStore.editCount)
    }

    @Test
    fun timedLockEndExplicitlyClearsToken() = runBlocking {
        val dataStore = PracticeFakeDataStore()
        val store = FirstPromisePracticeStore(dataStore)
        store.saveToken(FirstPromisePracticeToken("draft", 100L, 200L))

        store.clearToken()

        assertNull(store.readActiveToken(nowMillis = 150L))
        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN))
    }

    @Test
    fun tokenIsAtomicallyRemovedAtAndAfterExpiry() = runBlocking {
        val dataStore = PracticeFakeDataStore()
        val store = FirstPromisePracticeStore(dataStore)
        store.saveToken(FirstPromisePracticeToken("draft", 100L, 200L))
        val writesAfterSave = dataStore.editCount

        assertNull(store.readActiveToken(nowMillis = 200L))

        assertEquals(writesAfterSave + 1, dataStore.editCount)
        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN))
    }

    @Test
    fun serializedPracticeTokenHasNoPackageOrRoutineIdentityFields() {
        val json = Json.encodeToString(FirstPromisePracticeToken("draft-only", 100L, 200L))

        assertFalse(json.contains("packageName", ignoreCase = true))
        assertFalse(json.contains("routineId", ignoreCase = true))
        assertFalse(json.contains("package_name", ignoreCase = true))
        assertFalse(json.contains("routine_id", ignoreCase = true))
        assertEquals(true, json.contains("draft-only"))
    }

    @Test
    fun startedCommitProofRequiresExactAttemptAndDeadlineAndClearsOnlyThatAttempt() = runBlocking {
        val dataStore = PracticeFakeDataStore()
        val store = FirstPromisePracticeStore(dataStore)
        val token = FirstPromisePracticeToken(
            draftId = "draft",
            startedAtMillis = 100L,
            expiresAtMillis = 200L,
            attemptId = "attempt-current",
            encodedDeadline = "deadline-current",
        )
        val exact = FirstPromisePracticeAttempt(
            attemptId = token.attemptId,
            draftId = token.draftId,
            encodedDeadline = token.encodedDeadline,
        )

        store.saveStarted(token)

        assertTrue(store.isStartedAttemptCommitted(exact))
        assertFalse(store.isStartedAttemptCommitted(exact.copy(attemptId = "attempt-stale")))
        assertFalse(store.isStartedAttemptCommitted(exact.copy(encodedDeadline = "deadline-stale")))

        store.clearAttempt(exact)

        assertFalse(store.isStartedAttemptCommitted(exact))
        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN))
        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION))
    }

    @Test
    fun compensationClearsMatchingDecisionEvenWhenItsTokenWasAlreadyRemoved() = runBlocking {
        val dataStore = PracticeFakeDataStore()
        val store = FirstPromisePracticeStore(dataStore)
        val token = FirstPromisePracticeToken(
            draftId = "draft",
            startedAtMillis = 100L,
            expiresAtMillis = 200L,
            attemptId = "attempt-current",
            encodedDeadline = "deadline-current",
        )
        val attempt = FirstPromisePracticeAttempt(
            token.attemptId,
            token.draftId,
            token.encodedDeadline,
        )
        store.saveStarted(token)
        store.clearToken()

        store.clearAttempt(attempt)

        assertFalse(dataStore.snapshot().contains(PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION))
    }
}

private class PracticeFakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    var editCount: Int = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        editCount += 1
        val next = transform(state.value)
        state.value = next
        return next
    }

    fun snapshot(): Preferences = state.value
}
