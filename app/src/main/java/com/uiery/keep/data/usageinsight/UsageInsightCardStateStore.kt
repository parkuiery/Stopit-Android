package com.uiery.keep.data.usageinsight

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.domain.usageinsight.UsageInsightType
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val SUPPRESSION_DAYS = 7L
private const val MAX_PERMISSION_PROMPTS = 3
private const val PERMISSION_CARD_KEY = "permission"
private const val SEPARATOR = ","
private const val FIELD_SEPARATOR = "|"

/** 인사이트 카드 dismiss 상태. privacy-safe: 타입/날짜만 저장, 패키지명 저장 금지. */
class UsageInsightCardStateStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) {
    suspend fun suppressedTypes(today: LocalDate): Set<UsageInsightType> =
        readActive(today)
            .keys
            .mapNotNull { key -> UsageInsightType.entries.firstOrNull { it.analyticsValue == key } }
            .toSet()

    suspend fun isPermissionCardSuppressed(today: LocalDate): Boolean =
        PERMISSION_CARD_KEY in readActive(today)

    suspend fun recordDismissed(type: UsageInsightType, dismissedOn: LocalDate) =
        record(type.analyticsValue, dismissedOn)

    suspend fun recordPermissionCardDismissed(dismissedOn: LocalDate) =
        record(PERMISSION_CARD_KEY, dismissedOn)

    /**
     * 권한 카드는 dismiss 해도 [SUPPRESSION_DAYS] 뒤 다시 올라온다. 무한히 반복하면 조르는 것이
     * 되므로 누적 노출이 [MAX_PERMISSION_PROMPTS] 회에 이르면 더 권하지 않는다. 권한이 필요한
     * 지점에서는 여전히 직접 요청할 수 있다.
     */
    suspend fun isPermissionCardExhausted(): Boolean =
        permissionPromptCount() >= MAX_PERMISSION_PROMPTS

    suspend fun recordPermissionCardShown() {
        dataStore.edit { preferences ->
            val shown = preferences[PreferencesKey.USAGE_INSIGHT_PERMISSION_PROMPTS] ?: 0
            if (shown < MAX_PERMISSION_PROMPTS) {
                preferences[PreferencesKey.USAGE_INSIGHT_PERMISSION_PROMPTS] = shown + 1
            }
        }
    }

    private suspend fun permissionPromptCount(): Int =
        dataStore.data.first()[PreferencesKey.USAGE_INSIGHT_PERMISSION_PROMPTS] ?: 0

    private suspend fun readActive(today: LocalDate): Map<String, LocalDate> =
        decode(dataStore.data.first()[PreferencesKey.USAGE_INSIGHT_DISMISSED])
            .filterValues { dismissedOn -> !today.isAfter(dismissedOn.plusDays(SUPPRESSION_DAYS)) }

    private suspend fun record(key: String, dismissedOn: LocalDate) {
        dataStore.edit { preferences ->
            val next = decode(preferences[PreferencesKey.USAGE_INSIGHT_DISMISSED])
                .filterValues { !dismissedOn.isAfter(it.plusDays(SUPPRESSION_DAYS)) } + (key to dismissedOn)
            preferences[PreferencesKey.USAGE_INSIGHT_DISMISSED] =
                next.entries.joinToString(SEPARATOR) { (k, date) -> "$k$FIELD_SEPARATOR$date" }
        }
    }

    private fun decode(raw: String?): Map<String, LocalDate> =
        raw.orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null
                runCatching { parts[0] to LocalDate.parse(parts[1]) }.getOrNull()
            }
            .toMap()
}
