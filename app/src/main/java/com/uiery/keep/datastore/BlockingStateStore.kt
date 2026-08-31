package com.uiery.keep.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.uiery.keep.KeepDataSource
import com.uiery.keep.appselection.BlockExemptPackagePolicy
import com.uiery.keep.appselection.BlockExemptPackageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed access boundary for lock/session preferences stored in keep-datastore.
 *
 * Preference keys stay unchanged for backwards compatibility; feature and service code should use
 * this store instead of reading/writing raw [PreferencesKey] lock/session keys directly.
 *
 * Selected packages are filtered through [BlockExemptPackagePolicy] on the way in and out, so
 * device essentials an older build persisted via "select all" stop being blocked and stop crowding
 * the emergency unlock picker without needing a one-off data migration.
 */
@Singleton
class BlockingStateStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val blockExemptPackageProvider: BlockExemptPackageProvider = BlockExemptPackageProvider.None,
) {
    val accessibilitySnapshot: Flow<AccessibilityBlockingSnapshot> =
        dataStore.data.map { preferences ->
            AccessibilityBlockingSnapshot(
                isKeep = preferences[PreferencesKey.IS_KEEP] ?: false,
                lockTime = preferences[PreferencesKey.LOCK_TIME],
                selectedAppPackages = preferences.blockablePackages(),
                preventUninstall = preferences[PreferencesKey.PREVENT_UNINSTALL] ?: true,
                emergencyUnlockApps = preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS].orEmpty(),
                emergencyUnlockExpireTimeMillis = preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] ?: 0L,
            )
        }

    suspend fun readSelectedAppPackages(): Set<String> =
        dataStore.data.first().blockablePackages()

    suspend fun readSelectedWebDomains(): Set<String> =
        dataStore.data.first()[PreferencesKey.SELECTED_WEB_DOMAINS].orEmpty()

    suspend fun readSelectionState(): BlockingSelectionState {
        val preferences = dataStore.data.first()
        return BlockingSelectionState(
            selectedAppPackages = preferences.blockablePackages(),
            selectedWebDomains = preferences[PreferencesKey.SELECTED_WEB_DOMAINS].orEmpty(),
            hasTrackedFirstLockConfigured =
                preferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true ||
                    preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE] != null,
        )
    }

    suspend fun saveSelectedAppPackages(packages: Set<String>) {
        val blockablePackages = packages.blockable()
        dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = blockablePackages
        }
    }

    /**
     * Domains are not packages, so the package exemption policy does not apply to them.
     */
    suspend fun saveSelectedWebDomains(domains: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.SELECTED_WEB_DOMAINS] = domains
        }
    }

    private fun Preferences.blockablePackages(): Set<String> =
        this[PreferencesKey.SELECTED_APP_PACKAGES].orEmpty().blockable()

    private fun Set<String>.blockable(): Set<String> =
        BlockExemptPackagePolicy.filterBlockable(this, blockExemptPackageProvider.exemptPackages().homePackages)

    suspend fun setIsNew(isNew: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_NEW] = isNew
        }
    }

    suspend fun readIsNew(default: Boolean = true): Boolean =
        dataStore.data.first()[PreferencesKey.IS_NEW] ?: default

    suspend fun reserveFirstLockConfiguredDelivery(
        source: String,
        selectedAppCount: Int?,
    ): Boolean {
        var didReserve = false
        dataStore.edit { preferences ->
            val hasDelivered = preferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] == true
            val hasPending = preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE] != null
            if (!hasDelivered && !hasPending) {
                preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE] = source
                if (selectedAppCount == null) {
                    preferences.remove(PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT)
                } else {
                    preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT] =
                        selectedAppCount
                }
                didReserve = true
            }
        }
        return didReserve
    }

    suspend fun readPendingFirstLockConfiguredDelivery(): PendingFirstLockConfiguredDelivery? {
        val preferences = dataStore.data.first()
        val source = preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE] ?: return null
        return PendingFirstLockConfiguredDelivery(
            source = source,
            selectedAppCount = preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT],
        )
    }

    suspend fun markFirstLockConfiguredDeliveryCompleted(
        pending: PendingFirstLockConfiguredDelivery,
    ): Boolean {
        var didComplete = false
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE]?.let { source ->
                PendingFirstLockConfiguredDelivery(
                    source = source,
                    selectedAppCount =
                        preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT],
                )
            }
            if (current == pending) {
                preferences[PreferencesKey.HAS_TRACKED_FIRST_LOCK_CONFIGURED] = true
                preferences.remove(PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE)
                preferences.remove(PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT)
                didComplete = true
            }
        }
        return didComplete
    }

    suspend fun releaseFirstLockConfiguredDelivery(
        pending: PendingFirstLockConfiguredDelivery,
    ): Boolean {
        var didRelease = false
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE]?.let { source ->
                PendingFirstLockConfiguredDelivery(
                    source = source,
                    selectedAppCount =
                        preferences[PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT],
                )
            }
            if (current == pending) {
                preferences.remove(PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SOURCE)
                preferences.remove(PreferencesKey.PENDING_FIRST_LOCK_CONFIGURED_SELECTED_APP_COUNT)
                didRelease = true
            }
        }
        return didRelease
    }

    suspend fun markFirstOpenTrackedIfNeeded(timestampMillis: Long): Boolean {
        var didMark = false
        dataStore.edit { preferences ->
            val hasTracked = preferences[PreferencesKey.HAS_TRACKED_FIRST_OPEN] == true
            if (!hasTracked) {
                preferences[PreferencesKey.HAS_TRACKED_FIRST_OPEN] = true
                preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] = timestampMillis
                didMark = true
            }
        }
        return didMark
    }

    suspend fun readFirstCoreActionState(fallbackFirstOpenTimestampMillis: Long): FirstCoreActionState {
        val preferences = dataStore.data.first()
        return FirstCoreActionState(
            firstOpenTimestampMillis = preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] ?: fallbackFirstOpenTimestampMillis,
            hasTrackedFirstCoreAction = preferences[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION] == true,
        )
    }

    suspend fun markFirstCoreActionTracked(firstOpenTimestampMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_TRACKED_FIRST_CORE_ACTION] = true
            if (preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] == null) {
                preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] = firstOpenTimestampMillis
            }
        }
    }

    suspend fun incrementSuccessfulSessionCount(): Int {
        var next = 0
        dataStore.edit { preferences ->
            next = (preferences[PreferencesKey.SUCCESSFUL_SESSION_COUNT] ?: 0) + 1
            preferences[PreferencesKey.SUCCESSFUL_SESSION_COUNT] = next
        }
        return next
    }

    suspend fun readSuccessfulSessionCount(): Int =
        dataStore.data.first()[PreferencesKey.SUCCESSFUL_SESSION_COUNT] ?: 0

    suspend fun setIsKeep(isKeep: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.IS_KEEP] = isKeep
        }
    }

    suspend fun readIsKeep(): Boolean =
        dataStore.data.first()[PreferencesKey.IS_KEEP] ?: false

    suspend fun saveStartTime(timestampMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.START_TIME] = timestampMillis
        }
    }

    suspend fun readStartTime(): Long? =
        dataStore.data.first()[PreferencesKey.START_TIME]

    suspend fun saveLockTime(lockTime: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.LOCK_TIME] = lockTime
        }
    }

    suspend fun readLockTime(): String? =
        dataStore.data.first()[PreferencesKey.LOCK_TIME]

    /**
     * 예약된 만료 시각 전에 타이머 잠금을 내린다.
     *
     * 집중 세션을 중간에 끝냈을 때 쓴다. `LOCK_TIME` 을 남겨 두면 세션은 끝났는데 잠금만 예약된
     * 시각까지 계속 도는, 사용자가 어디서도 끌 수 없는 상태가 된다. 선택 앱 목록은 건드리지
     * 않는다 — 그건 세션이 아니라 사용자의 설정이다.
     */
    suspend fun clearTimedLockSession() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.LOCK_TIME)
            preferences.remove(PreferencesKey.START_TIME)
        }
    }

    suspend fun startTimedLockSession(
        packages: Set<String>,
        startTimeMillis: Long,
        encodedDeadline: String,
    ): TimedLockSessionOwnership {
        var ownership: TimedLockSessionOwnership? = null
        dataStore.edit { preferences ->
            ownership = TimedLockSessionOwnership(
                encodedDeadline = encodedDeadline,
                previous = TimedLockSessionSnapshot(
                    selectedAppPackages = preferences[PreferencesKey.SELECTED_APP_PACKAGES],
                    startTimeMillis = preferences[PreferencesKey.START_TIME],
                    encodedDeadline = preferences[PreferencesKey.LOCK_TIME],
                ),
            )
            preferences[PreferencesKey.SELECTED_APP_PACKAGES] = packages.blockable()
            preferences[PreferencesKey.START_TIME] = startTimeMillis
            preferences[PreferencesKey.LOCK_TIME] = encodedDeadline
        }
        return checkNotNull(ownership)
    }

    suspend fun rollbackTimedLockSession(ownership: TimedLockSessionOwnership): Boolean {
        var rolledBack = false
        dataStore.edit { preferences ->
            if (preferences[PreferencesKey.LOCK_TIME] == ownership.encodedDeadline) {
                preferences.restore(PreferencesKey.SELECTED_APP_PACKAGES, ownership.previous.selectedAppPackages)
                preferences.restore(PreferencesKey.START_TIME, ownership.previous.startTimeMillis)
                preferences.restore(PreferencesKey.LOCK_TIME, ownership.previous.encodedDeadline)
                rolledBack = true
            }
        }
        return rolledBack
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.restore(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    suspend fun setPreventUninstall(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.PREVENT_UNINSTALL] = enabled
        }
    }

    suspend fun saveEmergencyUnlockRuntimeState(apps: Set<String>, expireTimeMillis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.EMERGENCY_UNLOCK_APPS] = apps
            preferences[PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME] = expireTimeMillis
        }
    }

    suspend fun clearEmergencyUnlockRuntimeState() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_APPS)
            preferences.remove(PreferencesKey.EMERGENCY_UNLOCK_EXPIRE_TIME)
        }
    }

    /**
     * 해제 승인 시점의 completion payload 를 예약한다. 실제 기록은 창이 끝날 때 한다 (#1167).
     *
     * 앞선 창의 예약이 배달되지 않은 채 남아 있으면 덮어쓴다. `first_lock_configured` 와 달리
     * 이 이벤트는 설치당 1회가 아니라 해제 창당 1회이므로 영구 "배달 완료" 플래그를 두지 않는다.
     */
    suspend fun reserveEmergencyUnlockCompletion(
        reason: String,
        durationMinutes: Int,
        remainingUnlocks: Int,
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON] = reason
            preferences[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES] = durationMinutes
            preferences[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING] = remainingUnlocks
        }
    }

    suspend fun readPendingEmergencyUnlockCompletion(): PendingEmergencyUnlockCompletion? {
        val preferences = dataStore.data.first()
        return preferences.readPendingEmergencyUnlockCompletion()
    }

    /**
     * 배달된 예약을 제거한다. 읽은 뒤 값이 바뀌었으면(새 창이 시작됐으면) 제거하지 않아,
     * 새 창의 예약을 잘못 소비하지 않는다.
     */
    suspend fun markEmergencyUnlockCompletionDelivered(pending: PendingEmergencyUnlockCompletion): Boolean {
        var didComplete = false
        dataStore.edit { preferences ->
            if (preferences.readPendingEmergencyUnlockCompletion() == pending) {
                preferences.remove(PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON)
                preferences.remove(PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES)
                preferences.remove(PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING)
                didComplete = true
            }
        }
        return didComplete
    }

    private fun Preferences.readPendingEmergencyUnlockCompletion(): PendingEmergencyUnlockCompletion? {
        val reason = this[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON] ?: return null
        return PendingEmergencyUnlockCompletion(
            reason = reason,
            durationMinutes = this[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES] ?: 0,
            remainingUnlocks = this[PreferencesKey.PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING] ?: 0,
        )
    }
}

data class PendingEmergencyUnlockCompletion(
    val reason: String,
    val durationMinutes: Int,
    val remainingUnlocks: Int,
)

data class TimedLockSessionSnapshot(
    val selectedAppPackages: Set<String>?,
    val startTimeMillis: Long?,
    val encodedDeadline: String?,
)

data class TimedLockSessionOwnership(
    val encodedDeadline: String,
    val previous: TimedLockSessionSnapshot,
)

/**
 * The stored lock state the accessibility service reacts to.
 *
 * Device-role exemptions deliberately do not live here. This snapshot is re-derived only when
 * DataStore emits, so anything carried in it is frozen until the user next changes a preference —
 * fine for stored state, wrong for the default launcher or dialer, which the user can change at any
 * time without touching Stopit. The service resolves those at decision time instead.
 */
data class AccessibilityBlockingSnapshot(
    val isKeep: Boolean = false,
    val lockTime: String? = null,
    val selectedAppPackages: Set<String> = emptySet(),
    val preventUninstall: Boolean = true,
    val emergencyUnlockApps: Set<String> = emptySet(),
    val emergencyUnlockExpireTimeMillis: Long = 0L,
)

data class BlockingSelectionState(
    val selectedAppPackages: Set<String> = emptySet(),
    val selectedWebDomains: Set<String> = emptySet(),
    val hasTrackedFirstLockConfigured: Boolean = false,
)

data class PendingFirstLockConfiguredDelivery(
    val source: String,
    val selectedAppCount: Int?,
)

data class FirstCoreActionState(
    val firstOpenTimestampMillis: Long,
    val hasTrackedFirstCoreAction: Boolean,
)
