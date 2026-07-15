package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FirstPromisePracticeToken(
    val draftId: String,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val attemptId: String = "",
    val encodedDeadline: String = "",
)

data class FirstPromisePracticeAttempt(
    val attemptId: String,
    val draftId: String,
    val encodedDeadline: String,
)

@Serializable
enum class FirstPromisePracticeDecision {
    Started,
    Skipped,
}

@Serializable
private data class FirstPromisePracticeDecisionRecord(
    val draftId: String,
    val decision: FirstPromisePracticeDecision,
    val attemptId: String? = null,
    val encodedDeadline: String? = null,
)

interface FirstPromisePracticeStateStore {
    suspend fun saveStarted(token: FirstPromisePracticeToken)
    suspend fun recordSkippedIfAbsent(draftId: String): Boolean
    suspend fun readDecision(draftId: String): FirstPromisePracticeDecision?
    suspend fun isStartedAttemptCommitted(attempt: FirstPromisePracticeAttempt): Boolean
    suspend fun clearAttempt(attempt: FirstPromisePracticeAttempt)
}

@Singleton
class FirstPromisePracticeStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) : FirstPromisePracticeStateStore {
    suspend fun saveToken(token: FirstPromisePracticeToken) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN] = Json.encodeToString(token)
        }
    }

    override suspend fun saveStarted(token: FirstPromisePracticeToken) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN] = Json.encodeToString(token)
            preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION] = Json.encodeToString(
                FirstPromisePracticeDecisionRecord(
                    draftId = token.draftId,
                    decision = FirstPromisePracticeDecision.Started,
                    attemptId = token.attemptId,
                    encodedDeadline = token.encodedDeadline,
                ),
            )
        }
    }

    override suspend fun recordSkippedIfAbsent(draftId: String): Boolean {
        var recorded = false
        dataStore.edit { preferences ->
            val current = decodeDecision(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION])
            if (current?.draftId != draftId) {
                preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION] = Json.encodeToString(
                    FirstPromisePracticeDecisionRecord(draftId, FirstPromisePracticeDecision.Skipped),
                )
                recorded = true
            }
        }
        return recorded
    }

    override suspend fun readDecision(draftId: String): FirstPromisePracticeDecision? =
        decodeDecision(dataStore.data.first()[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION])
            ?.takeIf { it.draftId == draftId }
            ?.decision

    override suspend fun isStartedAttemptCommitted(attempt: FirstPromisePracticeAttempt): Boolean {
        val preferences = dataStore.data.first()
        val token = decode(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN])
        val decision = decodeDecision(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION])
        return token?.matches(attempt) == true &&
            decision?.matches(attempt) == true &&
            decision.decision == FirstPromisePracticeDecision.Started
    }

    override suspend fun clearAttempt(attempt: FirstPromisePracticeAttempt) {
        dataStore.edit { preferences ->
            val token = decode(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN])
            if (token?.matches(attempt) == true) {
                preferences.remove(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN)
            }
            val decision = decodeDecision(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION])
            if (decision?.matches(attempt) == true) {
                preferences.remove(PreferencesKey.FIRST_PROMISE_PRACTICE_DECISION)
            }
        }
    }

    suspend fun readActiveToken(nowMillis: Long): FirstPromisePracticeToken? {
        val current = decode(dataStore.data.first()[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN])
            ?: return null
        if (nowMillis < current.expiresAtMillis) {
            return current
        }

        var active: FirstPromisePracticeToken? = null
        dataStore.edit { preferences ->
            val latest = decode(preferences[PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN])
            if (latest != null && nowMillis >= latest.expiresAtMillis) {
                preferences.remove(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN)
            } else {
                active = latest
            }
        }
        return active
    }

    suspend fun activeAt(nowMillis: Long): FirstPromisePracticeToken? = readActiveToken(nowMillis)

    suspend fun clearToken() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.FIRST_PROMISE_PRACTICE_TOKEN)
        }
    }

    private fun decode(value: String?): FirstPromisePracticeToken? =
        value?.let { runCatching { Json.decodeFromString<FirstPromisePracticeToken>(it) }.getOrNull() }

    private fun decodeDecision(value: String?): FirstPromisePracticeDecisionRecord? =
        value?.let {
            runCatching { Json.decodeFromString<FirstPromisePracticeDecisionRecord>(it) }.getOrNull()
        }

    private fun FirstPromisePracticeToken.matches(attempt: FirstPromisePracticeAttempt): Boolean =
        attemptId == attempt.attemptId &&
            draftId == attempt.draftId &&
            encodedDeadline == attempt.encodedDeadline

    private fun FirstPromisePracticeDecisionRecord.matches(attempt: FirstPromisePracticeAttempt): Boolean =
        attemptId == attempt.attemptId &&
            draftId == attempt.draftId &&
            encodedDeadline == attempt.encodedDeadline
}
