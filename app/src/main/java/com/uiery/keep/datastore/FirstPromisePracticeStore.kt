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
)

interface FirstPromisePracticeStateStore {
    suspend fun saveStarted(token: FirstPromisePracticeToken)
    suspend fun recordSkippedIfAbsent(draftId: String): Boolean
    suspend fun readDecision(draftId: String): FirstPromisePracticeDecision?
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
                FirstPromisePracticeDecisionRecord(token.draftId, FirstPromisePracticeDecision.Started),
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
}
