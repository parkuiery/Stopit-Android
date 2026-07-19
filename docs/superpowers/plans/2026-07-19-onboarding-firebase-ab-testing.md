# Firebase A/B Testing Onboarding Assignment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace client-side onboarding rollout bucketing and three Remote Config flags with one Firebase A/B Testing string variant that is fetched before a fresh onboarding assignment.

**Architecture:** `FirebaseOnboardingExperimentConfig` becomes the sole interpreter of the `onboarding_variant` Remote Config value and returns a suspending typed resolution. `OnboardingEntryViewModel` consumes that resolution only for unassigned or active Treatment states, while `FirstPromiseDraftStore` keeps the chosen flow stable during onboarding and the existing emergency transition handles a readable server Control value.

**Tech Stack:** Kotlin 2.1, Firebase Remote Config through Firebase BoM 34.14.1, Hilt, Orbit MVI, Preferences DataStore, kotlinx.coroutines, JUnit4, Mockito, AndroidJUnit4

---

## File Map

### Production

- Modify `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentConfig.kt` — define the typed `OnboardingExperimentResolution` and suspending resolver interface.
- Modify `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt` — await fetch/activate, interpret the single string parameter, and fall back to activated cache safely.
- Delete `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicy.kt` — remove client-owned percentage bucketing.
- Modify `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryViewModel.kt` — consume the typed resolution, preserve existing Control, and use readable Control as the Treatment kill path.
- Modify `app/src/main/java/com/uiery/keep/feature/review/ReviewModule.kt` — replace the three onboarding defaults with `onboarding_variant=control`.

### Tests

- Rewrite `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt` — lock fetch ordering, source handling, valid variants, malformed values, and cache fallback.
- Delete `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicyTest.kt` — the deleted client bucketing policy has no replacement.
- Modify `app/src/test/java/com/uiery/keep/feature/onboarding/OnboardingEntryViewModelTest.kt` — lock the local/remote decision matrix and prove no bucket provider remains.
- Modify `app/src/test/java/com/uiery/keep/feature/review/FirebaseReviewRemoteConfigTest.kt` — lock the new shared defaults map.
- Modify `app/src/androidTest/java/com/uiery/keep/feature/onboarding/PromiseCoachOnboardingIntegrationTest.kt` — replace policy/snapshot fixtures with typed Firebase resolutions while preserving end-to-end Control and Treatment coverage.

### Documentation

- Keep `docs/superpowers/specs/2026-07-19-onboarding-firebase-ab-testing-design.md` as the parameter and console handoff source of truth.
- Update `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` only if its live operational section lacks the single-parameter A/B setup; do not rewrite historical evidence.

## Chunk 1: Atomic Firebase Resolution and Entry Routing

### Task 1: Lock the new Remote Config contract with failing tests

**Files:**
- Modify: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfigTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/review/FirebaseReviewRemoteConfigTest.kt`
- Modify: `app/src/test/java/com/uiery/keep/feature/onboarding/OnboardingEntryViewModelTest.kt`

- [ ] **Step 1: Replace the old snapshot expectations with the typed resolution API**

Use these exact contract values in the tests:

```kotlin
private const val VARIANT_KEY = "onboarding_variant"

OnboardingExperimentResolution(
    variant = OnboardingVariant.Control,
    remoteReadable = false,
)

OnboardingExperimentResolution(
    variant = OnboardingVariant.PromiseCoachV1,
    remoteReadable = true,
)
```

Add focused tests for:

```kotlin
@Test fun fetchesAndActivatesBeforeReadingTreatment()
@Test fun readsRemoteControlAsReadable()
@Test fun fetchFailureKeepsActivatedRemoteTreatmentReadable()
@Test fun fetchFailureKeepsActivatedRemoteControlReadable()
@Test fun fetchFailureWithoutActivatedRemoteValueFailsClosed()
@Test fun defaultOnlyValueFailsClosed()
@Test fun malformedRemoteValueBecomesReadableControl()
@Test fun valueConversionFailureBecomesUnreadableControl()
@Test fun cancelledFetchPropagatesCancellationWithoutReadingValue()
```

Stub `fetchAndActivate()` with `Tasks.forResult(true)` or `Tasks.forException(...)`, and verify it runs once before `getValue(VARIANT_KEY)` is observed.
Use `Tasks.forCanceled<Boolean>()` for the cancellation case and assert `CancellationException` while also verifying `getValue` is never called.

- [ ] **Step 2: Change the shared-defaults expectation**

The exact defaults map must be:

```kotlin
mapOf<String, Any>(
    "in_app_review_enabled" to true,
    "onboarding_variant" to "control",
)
```

Assert the retired keys are absent by matching the complete map.

- [ ] **Step 3: Replace Entry snapshot fixtures with a suspend fake resolver**

The helper must construct `OnboardingEntryViewModel` with an `OnboardingExperimentConfig` whose `resolve()` returns a supplied `OnboardingExperimentResolution`. Remove `bucketProvider` from test construction.

Add these separate decision tests:

```kotlin
@Test fun freshReadableTreatmentAssignsTreatment()
@Test fun freshUnreadableResultAssignsControl()
@Test fun existingControlDoesNotResolveRemoteAgain()
@Test fun existingControlStaysControlWhenTreatmentIsAvailable()
@Test fun existingTreatmentSurvivesUnreadableControlFallback()
@Test fun existingTreatmentContinuesForReadableTreatment()
@Test fun existingTreatmentAppliesEmergencyRecoveryForReadableControl()
@Test fun corruptedStateDoesNotResolveOrOverwriteRemoteAssignment()
```

Use a call-counting fake for the “does not resolve” tests. Preserve the existing all-phase emergency recovery assertions, but drive them with readable Control instead of `emergencyDisabled=true`.

- [ ] **Step 4: Run all affected tests and verify RED**

Run:

```bash
./gradlew :app:testDevDebugUnitTest \
  --tests '*FirebaseOnboardingExperimentConfigTest' \
  --tests '*FirebaseReviewRemoteConfigTest' \
  --tests '*OnboardingEntryViewModelTest'
```

Expected: FAIL because `OnboardingExperimentResolution` and `resolve()` do not exist, Entry still requires snapshots/buckets, and the production defaults still contain the three retired keys.

### Task 2: Implement the resolver and Entry migration as one compile-atomic change

**Files:**
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentConfig.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/FirebaseOnboardingExperimentConfig.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/review/ReviewModule.kt`
- Modify: `app/src/main/java/com/uiery/keep/feature/onboarding/entry/OnboardingEntryViewModel.kt`
- Modify: `app/src/androidTest/java/com/uiery/keep/feature/onboarding/PromiseCoachOnboardingIntegrationTest.kt`
- Delete: `app/src/main/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicy.kt`
- Delete: `app/src/test/java/com/uiery/keep/feature/onboarding/experiment/OnboardingExperimentPolicyTest.kt`

- [ ] **Step 1: Replace the snapshot interface**

Implement the complete public contract:

```kotlin
data class OnboardingExperimentResolution(
    val variant: OnboardingVariant = OnboardingVariant.Control,
    val remoteReadable: Boolean = false,
)

interface OnboardingExperimentConfig {
    suspend fun resolve(): OnboardingExperimentResolution
}
```

- [ ] **Step 2: Implement fetch/activate awaiting without a dependency**

Use `suspendCancellableCoroutine` around the existing Firebase `Task<Boolean>`. Guard every callback with `continuation.isActive`; cancel the continuation when the Firebase task is cancelled, resume with the task exception on failure, and resume with the task result on success.

The adapter algorithm must be exactly:

```kotlin
override suspend fun resolve(): OnboardingExperimentResolution {
    try {
        remoteConfig.fetchAndActivate().awaitResult()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // A previously activated remote value remains eligible below.
    }
    return readActivatedResolution()
}

private fun readActivatedResolution(): OnboardingExperimentResolution = try {
    val value = remoteConfig.getValue(VARIANT_KEY)
    if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
        OnboardingExperimentResolution()
    } else {
        OnboardingExperimentResolution(
            variant = when (value.asString()) {
                CONTROL_VALUE -> OnboardingVariant.Control
                TREATMENT_VALUE -> OnboardingVariant.PromiseCoachV1
                else -> OnboardingVariant.Control
            },
            remoteReadable = true,
        )
    }
} catch (_: Exception) {
    OnboardingExperimentResolution()
}
```

Do not add a percentage, boolean assignment gate, random provider, or new dependency.

- [ ] **Step 3: Migrate Entry before deleting the old contract**

Remove `Random`, `OnboardingExperimentPolicy`, snapshot fields, and `bucketProvider`. Inject/store a suspending `experimentResolution` function backed by `experimentConfig::resolve`.

Read local state first and preserve corruption handling. If the persisted assignment is Control, navigate to Intro without calling Firebase. Otherwise resolve once and reuse that result while waiting for persistence.

For an absent assignment, persist `resolution.variant` with assignment version V1. For persisted Treatment, call `recoverAfterRecreation()`, then call `applyEmergency()` only when `resolution.remoteReadable && resolution.variant == Control`. An unreadable Control fallback must not interrupt Treatment.

Keep the existing `Persisting` observation behavior and pass the same resolution through `awaitPersistenceNavigation` so it does not refetch in a loop.

- [ ] **Step 4: Migrate Android integration fixtures before deleting the policy**

Remove `OnboardingExperimentPolicy` and `OnboardingExperimentSnapshot` imports/assertions from `PromiseCoachOnboardingIntegrationTest`. Construct its Entry fake with:

```kotlin
override suspend fun resolve() = OnboardingExperimentResolution(
    variant = OnboardingVariant.Control,
    remoteReadable = true,
)
```

Use the equivalent readable Treatment resolution where a scenario needs `GoalSelect`. The Firebase adapter unit tests now own assignment parsing, so delete bucket-policy assertions rather than recreating them in androidTest.

- [ ] **Step 5: Replace shared defaults and delete the policy**

Set `"onboarding_variant" to "control"`, remove all three retired defaults, and delete the production/test percentage policy files.

- [ ] **Step 6: Compile the complete affected source sets**

Run:

```bash
./gradlew \
  :app:compileDevDebugKotlin \
  :app:compileDevDebugUnitTestKotlin \
  :app:compileDevDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`. This compile gate must pass before any focused test is described as green.

- [ ] **Step 7: Run all affected tests and verify GREEN**

Run the Task 1 Step 4 command again.

Expected: PASS with all three test classes green.

- [ ] **Step 8: Run neighboring analytics tests**

Run:

```bash
./gradlew :app:testDevDebugUnitTest \
  --tests '*OnboardingAnalyticsViewModelTest' \
  --tests '*IntroViewModelTest' \
  --tests '*GoalSelectViewModelTest'
```

Expected: PASS, proving Entry still does not emit exposure and visible Intro/GoalSelect paths retain exposure ownership.

- [ ] **Step 9: Commit the atomic assignment boundary**

Stage the complete resolver, Entry, defaults, policy deletions, focused tests, and migrated androidTest fixture together. Use a Lore-format commit whose intent is that Firebase owns experiment assignment, and record the complete compile, focused, and neighboring analytics commands in `Tested:`.

## Chunk 2: Integration Migration and Full Verification

### Task 3: Run Android integration coverage

**Files:**
- Verify: `app/src/androidTest/java/com/uiery/keep/feature/onboarding/PromiseCoachOnboardingIntegrationTest.kt`

- [ ] **Step 1: Run the migrated integration class**

Run:

```bash
./gradlew :app:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.uiery.keep.feature.onboarding.PromiseCoachOnboardingIntegrationTest
```

Expected: all tests in the class pass with zero failures. If no device is available, run `:app:compileDevDebugAndroidTestKotlin` and report the device gap rather than claiming runtime coverage.

### Task 4: Remove stale contract references and verify the feature

**Files:**
- Modify: `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md`
- Inspect: all tracked source and tests outside historical `docs/superpowers` artifacts

- [ ] **Step 1: Add the live Firebase A/B operational handoff**

Add a compact current-operation section to `docs/FIRST_LOCK_ACTIVATION_FUNNEL_RUNBOOK.md` that identifies `onboarding_variant`, default/Baseline `control`, Treatment `promise_coach_v1`, activation event `onboarding_experiment_exposed`, Firebase primary goal `app_block_intercepted`, and the ordered attributable 24-hour funnel as the product decision metric. State that console publication/FID targeting remain external evidence.

- [ ] **Step 2: Prove retired code and keys are absent**

Run:

```bash
rg -n \
  'OnboardingExperimentSnapshot|OnboardingExperimentPolicy|bucketProvider|onboarding_promise_coach_percent|onboarding_promise_coach_new_assignment_enabled|onboarding_promise_coach_emergency_disabled' \
  --glob '!docs/superpowers/**' \
  --glob '!.git/**' \
  --glob '!**/build/**' \
  .
```

Expected: no matches.

- [ ] **Step 3: Run the default JVM suite**

Run:

```bash
./gradlew :app:testDevDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 4: Run static analysis**

Run:

```bash
./gradlew :app:lintDevDebug
```

Expected: `BUILD SUCCESSFUL`, zero lint errors.

- [ ] **Step 5: Build the prod-like debug artifact**

Run:

```bash
./gradlew :app:assembleProdDebug
```

Expected: `BUILD SUCCESSFUL` and a generated prod debug APK.

- [ ] **Step 6: Verify the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat HEAD
```

Expected: no whitespace errors; only the approved A/B assignment, tests, integration fixture, and documentation changes are present.

- [ ] **Step 7: Commit integration and operational cleanup**

Use a Lore-format commit. `Tested:` must list the exact JVM, lint, build, and any device/instrumentation evidence that actually ran. If no device was available, record the androidTest compile command in `Tested:` and runtime instrumentation in `Not-tested:`. `Not-tested:` must always identify Firebase console publication and live experiment enrollment because those are external operations.
