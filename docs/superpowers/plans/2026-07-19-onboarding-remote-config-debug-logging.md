# Onboarding Remote Config Debug Logging Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the Firebase onboarding Remote Config fetch result, value source, raw `onboarding_variant`, and final typed resolution in debug Logcat only.

**Architecture:** Keep all diagnostics inside `FirebaseOnboardingExperimentConfig` and route production output through the existing `AppLogger` debug-only boundary under tag `OnboardingRemoteConfig`. Preserve the one-argument Hilt constructor and add an internal two-argument constructor for a test log callback so JVM tests can assert messages without Android Logcat.

**Tech Stack:** Kotlin, Firebase Remote Config, Hilt, coroutines, JUnit 4, Mockito, Gradle

---

## Chunk 1: Adapter logging and verification

### Task 1: Lock the debug logging contract with failing unit tests

**Files:**
- Modify: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt`
- Reference: `docs/superpowers/specs/2026-07-19-onboarding-remote-config-debug-logging-design.md`

- [ ] **Step 1: Add a test log collector**

Add a small record type and helper that construct the adapter through its planned internal callback constructor:

```kotlin
private data class LogEntry(
    val message: String,
    val throwable: Throwable?,
)

private fun adapterWithLogs(
    remoteConfig: FirebaseRemoteConfig,
    logs: MutableList<LogEntry>,
) = FirebaseOnboardingExperimentConfig(remoteConfig) { message, throwable ->
    logs += LogEntry(message, throwable)
}
```

- [ ] **Step 2: Add the successful remote-treatment logging test**

Assert the ordered message list is exactly:

```kotlin
listOf(
    "fetchAndActivate success",
    "source=remote",
    "rawValue=promise_coach_v1",
    "resolved variant=PromiseCoachV1 remoteReadable=true",
)
```

Also assert every collected throwable is null.

- [ ] **Step 3: Add the failed-fetch cached-control logging test**

Use a named `IllegalStateException("network")`. Assert the first message is `fetchAndActivate failed type=IllegalStateException; using activated value`, its throwable is the same instance, and the remaining messages identify remote source, raw `control`, and readable `Control`.

- [ ] **Step 4: Add the default-source and cancellation logging tests**

For a default value, assert the messages are success, `source=default`, and `resolved variant=Control remoteReadable=false`, with no `rawValue=` entry. For a cancelled fetch, assert `CancellationException` is propagated and the log collector stays empty.

- [ ] **Step 5: Run the focused test and confirm RED**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests '*FirebaseOnboardingExperimentConfigTest'
```

Expected: compilation fails because the internal callback constructor does not exist yet. This is the required TDD failure.

### Task 2: Implement minimal debug-only logging

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt`
- Test: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt`

- [ ] **Step 1: Add the centralized logging boundary and constructor delegation**

Import `com.uiery.keep.util.AppLogger`, add tag `OnboardingRemoteConfig`, and change construction to the following shape so Hilt never requests a function binding:

```kotlin
private const val LOG_TAG = "OnboardingRemoteConfig"

@Singleton
class FirebaseOnboardingExperimentConfig internal constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val logDebug: (message: String, throwable: Throwable?) -> Unit,
) : OnboardingExperimentConfig {
    @Inject
    constructor(remoteConfig: FirebaseRemoteConfig) : this(
        remoteConfig = remoteConfig,
        logDebug = { message, throwable -> AppLogger.debug(LOG_TAG, message, throwable) },
    )
}
```

- [ ] **Step 2: Log fetch success and non-cancellation failure**

After `fetchAndActivate().await()`, call `logDebug("fetchAndActivate success", null)`. In the non-cancellation catch, call:

```kotlin
logDebug(
    "fetchAndActivate failed type=${exception.javaClass.simpleName}; using activated value",
    exception,
)
```

Do not use `exception.message`; continue reading the activated cache as before.

- [ ] **Step 3: Log source and raw remote value while preserving mapping behavior**

Map Firebase source constants to `remote`, `default`, or `static`, then log `source=<label>`. Only when the source is remote, obtain `asString()`, log `rawValue=<value>`, and map `promise_coach_v1` to `PromiseCoachV1`; keep `control` and unknown values mapped to `Control`.

- [ ] **Step 4: Log the final resolution exactly once**

Store the `readActivatedVariant()` result in `resolution`, then call:

```kotlin
logDebug(
    "resolved variant=${resolution.variant} remoteReadable=${resolution.remoteReadable}",
    null,
)
```

Return that same object. Keep cancellation propagation unchanged; no final line is emitted if fetch cancellation aborts `resolve()`.

- [ ] **Step 5: Run the focused test and confirm GREEN**

Run:

```bash
./gradlew :app:testDevDebugUnitTest --tests '*FirebaseOnboardingExperimentConfigTest'
```

Expected: all `FirebaseOnboardingExperimentConfigTest` tests pass.

- [ ] **Step 6: Commit the tested implementation**

Stage only the adapter and its focused test and commit using the repository Lore protocol. Record the focused test command in the `Tested:` trailer.

### Task 3: Verify regression safety and production compilation

**Files:**
- Verify: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt`
- Verify: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt`

- [ ] **Step 1: Run the default JVM suite**

Run `./gradlew :app:testDevDebugUnitTest`.

Expected: BUILD SUCCESSFUL with the existing suite and new log assertions passing.

- [ ] **Step 2: Run static analysis**

Run `./gradlew :app:lintDevDebug`.

Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] **Step 3: Compile a production-like debug artifact**

Run `./gradlew :app:assembleProdDebug`.

Expected: BUILD SUCCESSFUL, proving the Hilt constructor and production source compile.

- [ ] **Step 4: Inspect the final diff**

Run `git diff --check`, inspect `git status --short --branch`, and confirm no unrelated source changes, new dependencies, Firebase keys, or production telemetry were introduced.

- [ ] **Step 5: Report the Logcat filter**

Document the local command:

```bash
adb logcat -s OnboardingRemoteConfig:D
```

The expected remote treatment sequence is fetch success, `source=remote`, `rawValue=promise_coach_v1`, and `resolved variant=PromiseCoachV1 remoteReadable=true`.
