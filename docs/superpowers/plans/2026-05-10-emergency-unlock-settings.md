# Emergency Unlock Settings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a menu-based emergency unlock settings screen and make both emergency unlock request surfaces consume the saved policy.

**Architecture:** Keep emergency-unlock settings in the existing Preferences DataStore, centralize validation and availability rules in `EmergencyUnlockPolicy.kt`, and reuse the existing emergency-unlock bottom sheet from both request surfaces. The new settings feature owns editing only; `LockViewModel` and `BlockViewModel` only consume sanitized settings and re-check policy before creating an unlock.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Hilt, Orbit MVI, Jetpack Navigation Compose, Android string resources, JUnit.

**Spec:** `docs/superpowers/specs/2026-05-10-emergency-unlock-settings-design.md`

---

## Chunk 1: Policy And DataStore Foundation

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Modify | `app/src/main/java/com/uiery/keep/service/EmergencyUnlockPolicy.kt` | Pure settings defaults, sanitization, availability, and remaining-count policy. |
| Modify | `app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt` | Regression tests for configurable emergency unlock policy. |
| Modify | `app/src/main/java/com/uiery/keep/datastore/DataStore.kt` | Preferences keys for user-configurable emergency unlock settings. |

### Task 1: Expand EmergencyUnlockPolicy

**Files:**
- Modify: `app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt`
- Modify: `app/src/main/java/com/uiery/keep/service/EmergencyUnlockPolicy.kt`

- [ ] **Step 1: Add failing policy tests**

Add tests for configurable limits, disabled policy, duration sanitization, and the fixed reason value.

```kotlin
@Test
fun configuredDailyLimitControlsReachedAndRemaining() {
    assertFalse(isEmergencyUnlockDailyLimitReached(dailyLimit = 5, todayUnlockCount = 4))
    assertTrue(isEmergencyUnlockDailyLimitReached(dailyLimit = 5, todayUnlockCount = 5))
    assertEquals(1, emergencyUnlockDailyRemaining(dailyLimit = 5, todayUnlockCount = 4))
    assertEquals(0, emergencyUnlockDailyRemaining(dailyLimit = 5, todayUnlockCount = 9))
}

@Test
fun disabledOrZeroLimitPolicyIsUnavailable() {
    assertFalse(isEmergencyUnlockAvailable(enabled = false, dailyLimit = 3, todayUnlockCount = 0))
    assertFalse(isEmergencyUnlockAvailable(enabled = true, dailyLimit = 0, todayUnlockCount = 0))
    assertTrue(isEmergencyUnlockAvailable(enabled = true, dailyLimit = 3, todayUnlockCount = 2))
}

@Test
fun invalidDailyLimitFallsBackToDefault() {
    assertEquals(3, sanitizeEmergencyUnlockDailyLimit(null))
    assertEquals(3, sanitizeEmergencyUnlockDailyLimit(-1))
    assertEquals(3, sanitizeEmergencyUnlockDailyLimit(6))
    assertEquals(5, sanitizeEmergencyUnlockDailyLimit(5))
}

@Test
fun durationOptionsAreFilteredSortedAndDefaulted() {
    assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(null))
    assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(emptySet()))
    assertEquals(listOf(3, 10), sanitizeEmergencyUnlockDurationOptions(setOf("10", "999", "3", "bad")))
    assertEquals(listOf(3, 5, 10), sanitizeEmergencyUnlockDurationOptions(setOf("999", "bad")))
}

@Test
fun reasonNotRequiredUsesStableReasonKey() {
    assertEquals("not_required", EMERGENCY_UNLOCK_REASON_NOT_REQUIRED)
}

@Test
fun requestCompletionGuardRejectsStaleSettings() {
    val settings = EmergencyUnlockSettings(
        enabled = true,
        dailyLimit = 3,
        durationOptions = listOf(3, 5),
        reasonRequired = true,
    )

    assertTrue(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings.copy(enabled = false),
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 3,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 10,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
        )
    )
}
```

- [ ] **Step 2: Run policy test to verify failure**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest" --no-daemon
```

Expected: FAIL because the new setting-aware policy functions and constants do not exist yet.

- [ ] **Step 3: Implement policy model and functions**

Replace the fixed-only policy with setting-aware functions while keeping backward-compatible overloads for existing call sites until later tasks update them.

```kotlin
package com.uiery.keep.service

internal const val DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT = 3
internal const val MIN_EMERGENCY_UNLOCK_DAILY_LIMIT = 0
internal const val MAX_EMERGENCY_UNLOCK_DAILY_LIMIT = 5
internal const val EMERGENCY_UNLOCK_REASON_NOT_REQUIRED = "not_required"

internal val ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS = listOf(3, 5, 10, 15)
internal val DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS = listOf(3, 5, 10)

@Deprecated("Use DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT")
internal const val DAILY_EMERGENCY_UNLOCK_LIMIT = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT

internal data class EmergencyUnlockSettings(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
    val reasonRequired: Boolean = true,
)

internal fun sanitizeEmergencyUnlockDailyLimit(value: Int?): Int =
    value
        ?.takeIf { it in MIN_EMERGENCY_UNLOCK_DAILY_LIMIT..MAX_EMERGENCY_UNLOCK_DAILY_LIMIT }
        ?: DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT

internal fun sanitizeEmergencyUnlockDurationOptions(values: Set<String>?): List<Int> {
    val sanitized = values
        .orEmpty()
        .mapNotNull { it.toIntOrNull() }
        .filter { it in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS }
        .distinct()
        .sorted()
    return sanitized.ifEmpty { DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS }
}

internal fun isEmergencyUnlockAvailable(
    enabled: Boolean,
    dailyLimit: Int,
    todayUnlockCount: Int,
): Boolean =
    enabled &&
        dailyLimit > 0 &&
        !isEmergencyUnlockDailyLimitReached(
            dailyLimit = dailyLimit,
            todayUnlockCount = todayUnlockCount,
        )

internal fun canCompleteEmergencyUnlockRequest(
    settings: EmergencyUnlockSettings,
    todayUnlockCount: Int,
    durationMinutes: Int,
    reason: String,
): Boolean =
    isEmergencyUnlockAvailable(
        enabled = settings.enabled,
        dailyLimit = settings.dailyLimit,
        todayUnlockCount = todayUnlockCount,
    ) &&
        durationMinutes in settings.durationOptions &&
        (!settings.reasonRequired || reason != EMERGENCY_UNLOCK_REASON_NOT_REQUIRED)

internal fun isEmergencyUnlockDailyLimitReached(
    dailyLimit: Int,
    todayUnlockCount: Int,
): Boolean =
    todayUnlockCount >= dailyLimit

internal fun isEmergencyUnlockDailyLimitReached(todayUnlockCount: Int): Boolean =
    isEmergencyUnlockDailyLimitReached(
        dailyLimit = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
        todayUnlockCount = todayUnlockCount,
    )

internal fun emergencyUnlockDailyRemaining(
    dailyLimit: Int,
    todayUnlockCount: Int,
): Int =
    (dailyLimit - todayUnlockCount).coerceAtLeast(0)

internal fun emergencyUnlockDailyRemaining(todayUnlockCount: Int): Int =
    emergencyUnlockDailyRemaining(
        dailyLimit = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
        todayUnlockCount = todayUnlockCount,
    )

internal fun isEmergencyUnlockActiveForPackage(
    packageName: String,
    unlockedApps: Set<String>,
    expireTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    unlockedApps.contains(packageName) && nowMillis < expireTimeMillis
```

- [ ] **Step 4: Run policy test to verify pass**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest" --no-daemon
```

Expected: PASS.

### Task 2: Add Preferences Keys

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/datastore/DataStore.kt`

- [ ] **Step 1: Add DataStore key import**

Add:

```kotlin
import androidx.datastore.preferences.core.intPreferencesKey
```

- [ ] **Step 2: Add setting keys**

Add to `PreferencesKey` near the existing emergency unlock keys:

```kotlin
val EMERGENCY_UNLOCK_ENABLED = booleanPreferencesKey("emergency_unlock_enabled")
val EMERGENCY_UNLOCK_DAILY_LIMIT = intPreferencesKey("emergency_unlock_daily_limit")
val EMERGENCY_UNLOCK_DURATION_OPTIONS = stringSetPreferencesKey("emergency_unlock_duration_options")
val EMERGENCY_UNLOCK_REASON_REQUIRED = booleanPreferencesKey("emergency_unlock_reason_required")
```

- [ ] **Step 3: Run targeted policy test**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Run build verification for DataStore key changes**

Run:

```bash
./gradlew :app:assembleDevDebug --no-daemon
```

Expected: PASS. This verifies the new main-source DataStore keys compile in the Android app, not only the pure policy tests.

- [ ] **Step 5: Commit Chunk 1**

Commit only files from this chunk. Use the repository Lore format and include the required OMX co-author trailer.

```bash
git add app/src/main/java/com/uiery/keep/service/EmergencyUnlockPolicy.kt app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt app/src/main/java/com/uiery/keep/datastore/DataStore.kt
git commit -m "Make emergency unlock policy configurable" \
  -m "The fixed emergency unlock limit needs a pure policy layer before UI and request surfaces can consume user settings." \
  -m "Constraint: Existing call sites still use the old fixed-limit helpers during the migration" \
  -m "Confidence: high" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew :app:testDevDebugUnitTest --tests \"com.uiery.keep.service.EmergencyUnlockPolicyTest\" --no-daemon" \
  -m "Tested: ./gradlew :app:assembleDevDebug --no-daemon" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

## Chunk 2: Settings Screen And Navigation

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Create | `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsNavigation.kt` | Type-safe route and NavGraph registration for settings screen. |
| Create | `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsViewModel.kt` | Read/write sanitized emergency unlock settings. |
| Create | `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsScreen.kt` | Compose UI for editing policy settings. |
| Modify | `app/src/main/java/com/uiery/keep/KeepApp.kt` | Register settings destination. |
| Modify | `app/src/main/java/com/uiery/keep/feature/menu/MenuNavigation.kt` | Add settings navigation callback. |
| Modify | `app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt` | Add menu item for settings screen. |
| Modify | `app/src/main/res/values/strings.xml` | Default English settings strings. |
| Modify | `app/src/main/res/values-ko/strings.xml` | Korean settings strings. |

### Task 3: Add Settings ViewModel

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsViewModel.kt`

- [ ] **Step 1: Create ViewModel with DataStore-backed state**

```kotlin
package com.uiery.keep.feature.emergencyunlocksettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.service.ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
import com.uiery.keep.service.MAX_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.MIN_EMERGENCY_UNLOCK_DAILY_LIMIT
import com.uiery.keep.service.sanitizeEmergencyUnlockDailyLimit
import com.uiery.keep.service.sanitizeEmergencyUnlockDurationOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyUnlockSettingsViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        val uiState: StateFlow<EmergencyUnlockSettingsUiState> =
            dataStore.data
                .map { preferences ->
                    EmergencyUnlockSettingsUiState(
                        enabled = preferences[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] ?: true,
                        dailyLimit = sanitizeEmergencyUnlockDailyLimit(
                            preferences[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT],
                        ),
                        durationOptions = sanitizeEmergencyUnlockDurationOptions(
                            preferences[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS],
                        ).toSet(),
                        reasonRequired = preferences[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] ?: true,
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = EmergencyUnlockSettingsUiState(),
                )

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[PreferencesKey.EMERGENCY_UNLOCK_ENABLED] = enabled }
            }
        }

        fun setDailyLimit(limit: Int) {
            viewModelScope.launch {
                dataStore.edit {
                    it[PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT] = sanitizeEmergencyUnlockDailyLimit(limit)
                }
            }
        }

        fun toggleDuration(minutes: Int) {
            if (minutes !in ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS) return
            viewModelScope.launch {
                dataStore.edit { preferences ->
                    val current = sanitizeEmergencyUnlockDurationOptions(
                        preferences[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS],
                    ).toSet()
                    val next =
                        if (minutes in current) {
                            if (current.size == 1) current else current - minutes
                        } else {
                            current + minutes
                        }
                    preferences[PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS] = next.map { it.toString() }.toSet()
                }
            }
        }

        fun setReasonRequired(required: Boolean) {
            viewModelScope.launch {
                dataStore.edit { it[PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED] = required }
            }
        }
    }

data class EmergencyUnlockSettingsUiState(
    val enabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val durationOptions: Set<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS.toSet(),
    val reasonRequired: Boolean = true,
    val allowedDailyLimits: IntRange = MIN_EMERGENCY_UNLOCK_DAILY_LIMIT..MAX_EMERGENCY_UNLOCK_DAILY_LIMIT,
    val allowedDurations: List<Int> = ALLOWED_EMERGENCY_UNLOCK_DURATION_OPTIONS,
)
```

- [ ] **Step 2: Run compile check**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: PASS or only fail on missing screen/navigation files that will be added in this chunk. If it fails on visibility of policy constants, make the policy declarations visible inside the app module by keeping them `internal`.

### Task 4: Add Settings Screen UI

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsScreen.kt`

- [ ] **Step 1: Create the screen**

Use Material3 controls already available in the app. Keep helper composables local to this feature file unless they are reused elsewhere during implementation.

```kotlin
package com.uiery.keep.feature.emergencyunlocksettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.KeepSwitch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmergencyUnlockSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EmergencyUnlockSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = null,
                            tint = Color(0xFFFE9E0B),
                        )
                    }
                },
                title = { Text(text = stringResource(R.string.emergency_unlock_settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KeepTheme.colors.background),
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.emergency_unlock_settings_enabled),
                subtitle = stringResource(R.string.emergency_unlock_settings_enabled_subtitle),
                checked = uiState.enabled,
                onCheckedChange = viewModel::setEnabled,
            )
            SettingSectionTitle(text = stringResource(R.string.emergency_unlock_settings_daily_limit))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.allowedDailyLimits.forEach { limit ->
                    FilterChip(
                        selected = uiState.dailyLimit == limit,
                        onClick = { viewModel.setDailyLimit(limit) },
                        label = {
                            Text(text = stringResource(R.string.emergency_unlock_settings_limit_count, limit))
                        },
                    )
                }
            }
            SettingSectionTitle(text = stringResource(R.string.emergency_unlock_settings_durations))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.allowedDurations.forEach { minutes ->
                    DurationOptionRow(
                        minutes = minutes,
                        checked = minutes in uiState.durationOptions,
                        onClick = { viewModel.toggleDuration(minutes) },
                    )
                }
            }
            SettingSwitchRow(
                title = stringResource(R.string.emergency_unlock_settings_reason_required),
                subtitle = stringResource(R.string.emergency_unlock_settings_reason_required_subtitle),
                checked = uiState.reasonRequired,
                onCheckedChange = viewModel::setReasonRequired,
            )
        }
    }
}
```

Add local helper composables below the screen:

```kotlin
@Composable
private fun SettingSectionTitle(text: String) {
    Text(
        text = text,
        color = KeepTheme.colors.onSurfaceVariant,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = KeepTheme.colors.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = KeepTheme.colors.onTertiaryContainer, fontSize = 12.sp)
        }
        KeepSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DurationOptionRow(
    minutes: Int,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = stringResource(R.string.emergency_unlock_duration_minutes, minutes),
            color = KeepTheme.colors.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: Remove unused imports after implementation**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: any failure should point to concrete missing imports/resources. Remove unused imports before moving on.

### Task 5: Add Navigation And Menu Entry

**Files:**
- Create: `app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings/EmergencyUnlockSettingsNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/KeepApp.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/menu/MenuNavigation.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt`

- [ ] **Step 1: Create navigation file**

```kotlin
package com.uiery.keep.feature.emergencyunlocksettings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object EmergencyUnlockSettingsRoute

fun NavController.navigateToEmergencyUnlockSettings(
    navOptions: NavOptions? = null,
) = navigate(route = EmergencyUnlockSettingsRoute, navOptions = navOptions)

fun NavGraphBuilder.emergencyUnlockSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<EmergencyUnlockSettingsRoute> {
        EmergencyUnlockSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
```

- [ ] **Step 2: Register destination in KeepApp**

Add imports:

```kotlin
import com.uiery.keep.feature.emergencyunlocksettings.emergencyUnlockSettingsScreen
import com.uiery.keep.feature.emergencyunlocksettings.navigateToEmergencyUnlockSettings
```

Pass the callback into `menuScreen`:

```kotlin
onNavigateEmergencyUnlockSettings = navController::navigateToEmergencyUnlockSettings,
```

Register the destination:

```kotlin
emergencyUnlockSettingsScreen(onNavigateBack = navController::navigateUp)
```

- [ ] **Step 3: Thread callback through MenuNavigation and MenuScreen**

In `MenuNavigation.kt`, add `onNavigateEmergencyUnlockSettings` to `menuScreen(...)` and pass it to `MenuScreen`.

In `MenuScreen.kt`, add the parameter:

```kotlin
onNavigateEmergencyUnlockSettings: () -> Unit,
```

Add a menu item near the lock-history item:

```kotlin
MenuItem(
    icon = R.drawable.ic_shield,
    title = stringResource(id = R.string.emergency_unlock_settings_title),
    onClick = onNavigateEmergencyUnlockSettings,
)
```

- [ ] **Step 5: Run compile check**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: FAIL only if string resources are still missing. If Kotlin fails, fix imports/signatures before adding resources.

### Task 6: Add Settings Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add settings strings**

Add English strings:

```xml
<string name="emergency_unlock_settings_title">Emergency unlock settings</string>
<string name="emergency_unlock_settings_enabled">Use emergency unlock</string>
<string name="emergency_unlock_settings_enabled_subtitle">Allow temporary unlocks from blocked screens</string>
<string name="emergency_unlock_settings_daily_limit">Daily unlock limit</string>
<string name="emergency_unlock_settings_limit_count">%d</string>
<string name="emergency_unlock_settings_durations">Available unlock durations</string>
<string name="emergency_unlock_settings_reason_required">Ask unlock reason</string>
<string name="emergency_unlock_settings_reason_required_subtitle">Show the reason step before selecting apps</string>
```

Add Korean strings:

```xml
<string name="emergency_unlock_settings_title">긴급 해제 설정</string>
<string name="emergency_unlock_settings_enabled">긴급 해제 사용</string>
<string name="emergency_unlock_settings_enabled_subtitle">차단 화면에서 임시 해제를 요청할 수 있어요</string>
<string name="emergency_unlock_settings_daily_limit">하루 허용 횟수</string>
<string name="emergency_unlock_settings_limit_count">%d</string>
<string name="emergency_unlock_settings_durations">허용할 해제 시간</string>
<string name="emergency_unlock_settings_reason_required">해제 이유 묻기</string>
<string name="emergency_unlock_settings_reason_required_subtitle">앱 선택 전에 이유 선택 단계를 보여줘요</string>
```

- [ ] **Step 2: Run resource/build check**

Run:

```bash
./gradlew :app:assembleDevDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Commit Chunk 2**

```bash
git add app/src/main/java/com/uiery/keep/feature/emergencyunlocksettings app/src/main/java/com/uiery/keep/KeepApp.kt app/src/main/java/com/uiery/keep/feature/menu/MenuNavigation.kt app/src/main/java/com/uiery/keep/feature/menu/MenuScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-ko/strings.xml
git commit -m "Add emergency unlock settings screen" \
  -m "Users need a stable pre-blocking place to tune emergency unlock policy, so the menu now owns policy editing while lock surfaces remain consumers." \
  -m "Constraint: No new design-system component or dependency" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :app:assembleDevDebug --no-daemon" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

## Chunk 3: Request Surface Integration

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Modify | `app/src/main/java/com/uiery/keep/feature/lock/component/EmergencyUnlockBottomSheetContent.kt` | Accept configured durations and optional reason step. |
| Modify | `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt` | Read settings, calculate configured limits, and re-check before unlock. |
| Modify | `app/src/main/java/com/uiery/keep/feature/lock/LockScreen.kt` | Pass settings to bottom sheet and display dynamic counts. |
| Modify | `app/src/main/java/com/uiery/keep/BlockViewModel.kt` | Same policy consumption as LockViewModel for blocked-app path. |
| Modify | `app/src/main/java/com/uiery/keep/BlockScreen.kt` | Same bottom-sheet settings and dynamic counts as lock path. |
| Modify | `app/src/main/res/values*/strings.xml` | Update emergency unlock count strings to use configurable limits. |

### Task 7: Make BottomSheet Configurable

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/feature/lock/component/EmergencyUnlockBottomSheetContent.kt`

- [ ] **Step 1: Replace fixed duration list with parameters**

Remove the private fixed `DURATION_OPTIONS` constant. Add parameters:

```kotlin
fun EmergencyUnlockBottomSheetContent(
    blockedApps: Set<String>,
    durationOptions: List<Int>,
    reasonStepEnabled: Boolean,
    onUnlock: (reason: String, customReason: String?, apps: Set<String>, durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
)
```

- [ ] **Step 2: Initialize step and selected duration from settings**

Use the first sanitized duration so the current default of `5` cannot become stale when `5` is not allowed.

```kotlin
val safeDurationOptions = remember(durationOptions) {
    durationOptions.ifEmpty { DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS }
}
var step by remember(reasonStepEnabled) {
    mutableStateOf(if (reasonStepEnabled) UnlockStep.REASON else UnlockStep.APPS)
}
var selectedDuration by remember(safeDurationOptions) {
    mutableIntStateOf(safeDurationOptions.first())
}
```

- [ ] **Step 3: Adjust step indicator and duration step**

Compute visible non-countdown steps explicitly so the first visible step is displayed as step 1.

```kotlin
val visibleSteps = remember(reasonStepEnabled) {
    if (reasonStepEnabled) {
        listOf(UnlockStep.REASON, UnlockStep.APPS, UnlockStep.DURATION)
    } else {
        listOf(UnlockStep.APPS, UnlockStep.DURATION)
    }
}
```

Use:

```kotlin
StepIndicator(
    currentStep = visibleSteps.indexOf(step).coerceAtLeast(0),
    totalSteps = visibleSteps.size,
)
```

In `DurationStep`, iterate over `safeDurationOptions`.

- [ ] **Step 4: Save stable reason when reason step is disabled**

In `CountdownStep.onComplete`, pass the fixed key and null custom reason when the reason step is disabled.

```kotlin
val reason = if (reasonStepEnabled) selectedReason.orEmpty() else EMERGENCY_UNLOCK_REASON_NOT_REQUIRED
val custom = if (reasonStepEnabled && selectedReason == "other") customReason else null
onUnlock(reason, custom, selectedApps, selectedDuration)
```

Add import:

```kotlin
import com.uiery.keep.service.EMERGENCY_UNLOCK_REASON_NOT_REQUIRED
import com.uiery.keep.service.DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS
```

- [ ] **Step 5: Run compile check**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: FAIL because call sites still need to pass new parameters. Proceed to next tasks.

### Task 8: Update LockViewModel And LockScreen

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/lock/LockScreen.kt`

- [ ] **Step 1: Add settings fields to LockUiState**

```kotlin
val emergencyUnlockEnabled: Boolean = true,
val emergencyUnlockDailyLimit: Int = DEFAULT_EMERGENCY_UNLOCK_DAILY_LIMIT,
val emergencyUnlockDurationOptions: List<Int> = DEFAULT_EMERGENCY_UNLOCK_DURATION_OPTIONS,
val emergencyUnlockReasonRequired: Boolean = true,
```

- [ ] **Step 2: Add a private settings loader**

Add imports for the new policy constants/functions. Then add:

```kotlin
private suspend fun readEmergencyUnlockSettings(): EmergencyUnlockSettings {
    val preferences = dataStore.data.firstOrNull()
    return EmergencyUnlockSettings(
        enabled = preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_ENABLED) ?: true,
        dailyLimit = sanitizeEmergencyUnlockDailyLimit(
            preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_DAILY_LIMIT),
        ),
        durationOptions = sanitizeEmergencyUnlockDurationOptions(
            preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_DURATION_OPTIONS),
        ),
        reasonRequired = preferences?.get(PreferencesKey.EMERGENCY_UNLOCK_REASON_REQUIRED) ?: true,
    )
}
```

- [ ] **Step 3: Load settings during init**

Call `loadEmergencyUnlockSettings()` before `checkDailyLimit()` inside `initIntent()`.

```kotlin
private fun loadEmergencyUnlockSettings() = intent {
    val settings = readEmergencyUnlockSettings()
    reduce {
        state.copy(
            emergencyUnlockEnabled = settings.enabled,
            emergencyUnlockDailyLimit = settings.dailyLimit,
            emergencyUnlockDurationOptions = settings.durationOptions,
            emergencyUnlockReasonRequired = settings.reasonRequired,
        )
    }
}
```

- [ ] **Step 4: Update `checkDailyLimit()`**

Use `readEmergencyUnlockSettings()` and configured limit:

```kotlin
val settings = readEmergencyUnlockSettings()
val count = emergencyUnlockDao.countToday(todayStart)
reduce {
    state.copy(
        emergencyUnlockEnabled = settings.enabled,
        emergencyUnlockDailyLimit = settings.dailyLimit,
        emergencyUnlockDurationOptions = settings.durationOptions,
        emergencyUnlockReasonRequired = settings.reasonRequired,
        dailyLimitReached = !isEmergencyUnlockAvailable(settings.enabled, settings.dailyLimit, count),
        dailyUnlockRemaining = emergencyUnlockDailyRemaining(settings.dailyLimit, count),
    )
}
```

- [ ] **Step 5: Re-check settings before unlock**

At the start of `emergencyUnlock(...)`, load settings and count again. If unavailable or `durationMinutes !in settings.durationOptions`, call `checkDailyLimit()` and return before writing `EmergencyUnlockState`, DataStore, Room, or analytics.

Also recompute the analytics remaining count from the fresh settings/count, not from stale UI state:

```kotlin
val todayCount = emergencyUnlockDao.countToday(todayStart)
if (!canCompleteEmergencyUnlockRequest(
        settings = settings,
        todayUnlockCount = todayCount,
        durationMinutes = durationMinutes,
        reason = reason,
    )
) {
    checkDailyLimit()
    return@intent
}
val unlockCountRemaining = emergencyUnlockDailyRemaining(
    dailyLimit = settings.dailyLimit,
    todayUnlockCount = todayCount + 1,
)
```

- [ ] **Step 6: Update LockScreen call site and count display**

Pass settings to `EmergencyUnlockBottomSheetContent`:

```kotlin
durationOptions = uiState.emergencyUnlockDurationOptions,
reasonStepEnabled = uiState.emergencyUnlockReasonRequired,
```

Change count string call to:

```kotlin
stringResource(
    R.string.emergency_unlock_with_count,
    uiState.dailyUnlockRemaining,
    uiState.emergencyUnlockDailyLimit,
)
```

Disable the button when `dailyLimitReached` is true. Because `checkDailyLimit()` sets that to true for disabled or zero-limit policy, no extra UI branch is required.

- [ ] **Step 7: Run compile check**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: FAIL until `BlockScreen` call site is updated. Proceed to the next task.

### Task 9: Update BlockViewModel And BlockScreen

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/BlockViewModel.kt`
- Modify: `app/src/main/java/com/uiery/keep/BlockScreen.kt`

- [ ] **Step 1: Mirror settings state in BlockUiState**

Add the same settings fields used by `LockUiState`.

- [ ] **Step 2: Add settings loader and update checkDailyLimit**

Use the same `readEmergencyUnlockSettings()` shape as `LockViewModel`. Because both ViewModels need the same parsing, keep the logic identical for now; extract later only if the implementation becomes materially repetitive.

- [ ] **Step 3: Re-check settings before unlock**

At the start of `BlockViewModel.emergencyUnlock(...)`, load current settings and today count. Call `canCompleteEmergencyUnlockRequest(...)` with the current settings, today count, selected duration, and reason. If it returns false, refresh state and return before writing `EmergencyUnlockState`, DataStore active unlock keys, Room history, or analytics. Recompute `unlockCountRemaining` from `settings.dailyLimit` and `todayCount + 1`, matching the `LockViewModel` snippet.

- [ ] **Step 4: Update BlockScreen call site and count display**

Pass `durationOptions` and `reasonStepEnabled` into `EmergencyUnlockBottomSheetContent`.

Update the count string call:

```kotlin
stringResource(
    R.string.emergency_unlock_with_count,
    uiState.dailyUnlockRemaining,
    uiState.emergencyUnlockDailyLimit,
)
```

### Task 10: Update Dynamic Count Strings

**Files:**
- Modify: every `app/src/main/res/values*/strings.xml` that defines `emergency_unlock_with_count` or `emergency_unlock_remaining_count`.

- [ ] **Step 1: Change count strings to accept configured limit**

Update `emergency_unlock_with_count` and `emergency_unlock_remaining_count` in every locale from hardcoded `/3` to two placeholders.

English:

```xml
<string name="emergency_unlock_with_count">Emergency Unlock (%1$d/%2$d)</string>
<string name="emergency_unlock_remaining_count">%1$d/%2$d remaining</string>
```

Korean:

```xml
<string name="emergency_unlock_with_count">긴급 해제 (%1$d/%2$d)</string>
<string name="emergency_unlock_remaining_count">%1$d/%2$d 남음</string>
```

For other locales, preserve local language text but change the numeric fraction to `%1$d/%2$d`. Do not leave translated strings with a different placeholder count.

- [ ] **Step 2: Confirm no hardcoded count remains**

Run:

```bash
rg -n "emergency_unlock_(with_count|remaining_count).*\\/3" app/src/main/res/values*
```

Expected: no output.

- [ ] **Step 3: Confirm localized count strings use two placeholders**

Run:

```bash
for f in app/src/main/res/values*/strings.xml; do
  if rg -q 'name="emergency_unlock_(with_count|remaining_count)"' "$f"; then
    rg 'name="emergency_unlock_(with_count|remaining_count)"' "$f" | while IFS= read -r line; do
      case "$line" in
        *'%1$d'*'%2$d'*) ;;
        *) echo "$f:$line"; exit 1 ;;
      esac
    done || exit 1
  fi
done
```

Expected: no output and exit code 0.

- [ ] **Step 4: Run compile check**

Run:

```bash
./gradlew :app:compileDevDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Inspect stale guard placement before commit**

Inspect both `LockViewModel.emergencyUnlock(...)` and `BlockViewModel.emergencyUnlock(...)`. Confirm `canCompleteEmergencyUnlockRequest(...)` runs before every write to `EmergencyUnlockState`, DataStore active unlock keys, Room history, and analytics.

- [ ] **Step 6: Run request-surface manual checklist before commit**

If a device/emulator is available, verify both request surfaces before committing:

- `LockScreen` bottom sheet shows only configured durations.
- `BlockScreen` bottom sheet shows only configured durations.
- With reason step disabled, both surfaces start at app selection.
- With emergency unlock disabled or daily limit 0, both surfaces block request completion.
- Stale reason policy: open a sheet while reason step is disabled, re-enable reason in settings before countdown completes, then verify completion is blocked or refreshed and no `not_required` unlock is written.

If no device/emulator is available, record this as `Not-tested` in the commit message and continue only after compile checks pass.

- [ ] **Step 7: Run resource/build check**

Run:

```bash
./gradlew :app:assembleDevDebug --no-daemon
```

Expected: PASS after call sites and string placeholders are updated together.

- [ ] **Step 8: Commit Chunk 3**

```bash
git add app/src/main/java/com/uiery/keep/feature/lock/component/EmergencyUnlockBottomSheetContent.kt app/src/main/java/com/uiery/keep/feature/lock/LockViewModel.kt app/src/main/java/com/uiery/keep/feature/lock/LockScreen.kt app/src/main/java/com/uiery/keep/BlockViewModel.kt app/src/main/java/com/uiery/keep/BlockScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-ja/strings.xml app/src/main/res/values-ko/strings.xml app/src/main/res/values-nl/strings.xml app/src/main/res/values-pt/strings.xml app/src/main/res/values-pt-rBR/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "Apply emergency unlock settings to request surfaces" \
  -m "Both lock and blocked-app request paths need to consume the same saved policy before creating an active emergency unlock." \
  -m "Constraint: AccessibilityService active-unlock enforcement remains unchanged" \
  -m "Confidence: medium" \
  -m "Scope-risk: moderate" \
  -m "Tested: ./gradlew :app:compileDevDebugKotlin --no-daemon" \
  -m "Tested: ./gradlew :app:assembleDevDebug --no-daemon" \
  -m "Not-tested: Device/emulator manual request-surface pass if unavailable" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

## Chunk 4: Verification And Final Hardening

## File Map

| Action | File | Responsibility |
| --- | --- | --- |
| Modify | `app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt` | Add stale-settings-related policy coverage if not already covered by ViewModel tests. |
| Verify | `app/src/main/res/values*/strings.xml` | Ensure emergency unlock count string placeholders match across localized resources. |
| Verify | Gradle commands | Build, unit-test, and resource placeholder validation. |

### Task 11: Add Any Missing Regression Coverage

**Files:**
- Modify: `app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt`
- Optional Test: ViewModel tests if the current project has or can easily add a DataStore-backed ViewModel test harness without new dependencies.

- [ ] **Step 1: Confirm stale-settings guard tests exist**

Chunk 1 should already add `requestCompletionGuardRejectsStaleSettings()` for `canCompleteEmergencyUnlockRequest(...)`. Confirm the test covers:

- disabled policy after a sheet was opened
- daily limit reached after a sheet was opened
- selected duration removed after a sheet was opened
- reason step re-enabled after a sheet was opened with `reason = "not_required"`

If that test is missing, add it:

```kotlin
@Test
fun requestCompletionGuardRejectsStaleSettings() {
    val settings = EmergencyUnlockSettings(
        enabled = true,
        dailyLimit = 3,
        durationOptions = listOf(3, 5),
        reasonRequired = true,
    )

    assertTrue(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings.copy(enabled = false),
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 3,
            durationMinutes = 3,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 10,
            reason = "work",
        )
    )
    assertFalse(
        canCompleteEmergencyUnlockRequest(
            settings = settings,
            todayUnlockCount = 0,
            durationMinutes = 3,
            reason = EMERGENCY_UNLOCK_REASON_NOT_REQUIRED,
        )
    )
}
```

Do not add mocking, coroutine-test, or other new dependencies for ViewModel tests in this feature. The required stale-settings invariant is: both `LockViewModel` and `BlockViewModel` must call `canCompleteEmergencyUnlockRequest(...)` immediately before any active unlock state, Room history, DataStore, or analytics write.

- [ ] **Step 2: Run unit tests**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests "com.uiery.keep.service.EmergencyUnlockPolicyTest" --no-daemon
```

Expected: PASS.

### Task 12: Verify Localized Count Placeholders

**Files:**
- Verify: every `app/src/main/res/values*/strings.xml` that defines `emergency_unlock_with_count` or `emergency_unlock_remaining_count`.

- [ ] **Step 1: Search for hardcoded `/3`**

Run:

```bash
rg -n "emergency_unlock_(with_count|remaining_count).*\\/3" app/src/main/res/values*
```

Expected: no output.

- [ ] **Step 2: Confirm all localized count strings have two placeholders**

Run:

```bash
for f in app/src/main/res/values*/strings.xml; do
  if rg -q 'name="emergency_unlock_(with_count|remaining_count)"' "$f"; then
    rg 'name="emergency_unlock_(with_count|remaining_count)"' "$f" | while IFS= read -r line; do
      case "$line" in
        *'%1$d'*'%2$d'*) ;;
        *) echo "$f:$line"; exit 1 ;;
      esac
    done || exit 1
  fi
done
```

Expected: no output and exit code 0.

### Task 13: Full Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run unit tests**

Run:

```bash
./gradlew test --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run lint/static analysis**

Run:

```bash
./gradlew lint --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run debug assembly**

Run:

```bash
./gradlew assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Manual verification checklist**

Install/run a debug build if device access is available. Verify:

- Menu opens `Emergency unlock settings`.
- Settings persist after navigating away and back.
- Duration options selected in settings appear in `LockScreen` emergency unlock sheet.
- Duration options selected in settings appear in `BlockScreen` emergency unlock sheet.
- If `5 minutes` is not selected, the bottom sheet initially selects the first allowed duration.
- Disabling the reason step starts the sheet at app selection and saves `reason = "not_required"` with `customReason = null`.
- Disabling emergency unlock prevents a new unlock request from both request surfaces.
- Setting daily limit to 0 prevents a new unlock request from both request surfaces.
- Stale guard code path: confirm both `LockViewModel.emergencyUnlock(...)` and `BlockViewModel.emergencyUnlock(...)` call `canCompleteEmergencyUnlockRequest(...)` before any `EmergencyUnlockState`, DataStore active unlock, Room history, or analytics write.
- Stale disabled policy: if a debug/manual way to mutate DataStore while a sheet remains open is available, open a request sheet, disable emergency unlock before countdown completes, then verify completion is blocked or refreshed and no active unlock state/history/analytics write occurs. If there is no such mechanism, record this as `Not-tested` and rely on the pure guard test plus code-path inspection above.
- Stale duration policy: if a debug/manual way to mutate DataStore while a sheet remains open is available, open a request sheet with a selected duration, remove that duration before countdown completes, then verify completion is blocked or refreshed and no active unlock state/history/analytics write occurs. If there is no such mechanism, record this as `Not-tested` and rely on the pure guard test plus code-path inspection above.
- Stale reason policy: if a debug/manual way to mutate DataStore while a sheet remains open is available, open a request sheet while reason step is disabled, re-enable reason before countdown completes, then verify completion is blocked or refreshed and no `not_required` unlock is written. If there is no such mechanism, record this as `Not-tested` and rely on the pure guard test plus code-path inspection above.
- Existing 30 second countdown still runs before unlock completion.

If no device/emulator is available, do not mark manual verification as complete. Record the full manual checklist as `Not-tested` in the final report. In the final commit message, replace the generic manual-pass trailer with specific trailers for any skipped manual items, for example:

```text
Not-tested: Device/emulator manual pass unavailable; menu navigation, persistence, both request surfaces, and countdown were not manually exercised
Not-tested: No debug/manual DataStore mutation path was available for stale open-sheet disabled/duration/reason scenarios
```

- [ ] **Step 5: Final commit**

If Tasks 10-12 changed files, commit them. If they were verification-only, skip this commit.

```bash
git add app/src/test/java/com/uiery/keep/service/EmergencyUnlockPolicyTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-it/strings.xml app/src/main/res/values-ja/strings.xml app/src/main/res/values-ko/strings.xml app/src/main/res/values-nl/strings.xml app/src/main/res/values-pt/strings.xml app/src/main/res/values-pt-rBR/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "Harden emergency unlock settings verification" \
  -m "Configurable emergency unlock policy needs localized count placeholders and tests that protect stale-setting behavior." \
  -m "Confidence: medium" \
  -m "Scope-risk: narrow" \
  -m "Tested: ./gradlew test --no-daemon" \
  -m "Tested: ./gradlew lint --no-daemon" \
  -m "Tested: ./gradlew assembleDebug --no-daemon" \
  -m "Not-tested: Replace with exact skipped manual checklist items if device/emulator or stale DataStore mutation path is unavailable" \
  -m "Co-authored-by: OmX <omx@oh-my-codex.dev>"
```

---

## Implementation Notes

- The working tree already contained unrelated modified files before this planning step. Do not revert or fold unrelated changes into this feature.
- `docs/superpowers/` is ignored by `.gitignore`; force-add plan/spec documents only when intentionally committing them.
- Do not change the Room `emergency_unlock` table for this feature.
- Do not move active unlock enforcement into `KeepAccessibilityService`; it should keep checking only active unlocked apps and expiry.
- Do not add new dependencies.
- Prefer small local UI helpers inside `feature/emergencyunlocksettings` over adding KDS components.

## Final Expected Verification

Run before reporting implementation complete:

```bash
./gradlew test --no-daemon
./gradlew lint --no-daemon
./gradlew assembleDebug --no-daemon
rg -n "emergency_unlock_(with_count|remaining_count).*\\/3" app/src/main/res/values*
```

Expected:

- Gradle `test` passes.
- Gradle `lint` passes.
- Gradle `assembleDebug` passes.
- `rg` returns no hardcoded `/3` emergency unlock count strings.
