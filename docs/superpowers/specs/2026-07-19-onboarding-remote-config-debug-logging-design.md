# Onboarding Remote Config Debug Logging Design

## Goal

Make Firebase onboarding variant resolution observable in local Logcat without emitting configuration diagnostics from production builds.

## Decision

`FirebaseOnboardingExperimentConfig` logs through the existing `AppLogger` boundary with tag `OnboardingRemoteConfig`. Because `AppLogger` returns immediately when `BuildConfig.DEBUG` is false, production builds produce no Logcat output.

The resolver logs only non-sensitive experiment diagnostics:

- fetch/activate success
- fetch/activate failure class and throwable
- Remote Config value source
- raw `onboarding_variant` string when the value came from Remote Config
- final typed resolution: `Control` or `PromiseCoachV1`, plus `remoteReadable`

It must not log Firebase Installation ID, auth token, package usage, selected apps, usage history, draft/routine IDs, or Analytics payloads.
Failure messages include only the exception class; they never interpolate exception messages or remote payloads. The throwable is passed separately to `AppLogger.debug` for local stack-trace inspection.

## Alternatives

- Direct `android.util.Log`: rejected because production source is required to use the centralized debug-only boundary.
- Crashlytics logging: rejected because this is local QA diagnostics, not a production error signal.
- Log only the final variant: rejected because it cannot distinguish fetch failure, default-only fallback, malformed remote value, and a real remote Control assignment.

## Event Points

1. After `fetchAndActivate()` completes, log `fetchAndActivate success`.
2. On a non-cancellation fetch exception, log `fetchAndActivate failed` with the throwable, then continue using activated cache.
3. Before mapping, log the Firebase value source using a stable label: `remote`, `default`, or `static`.
4. For a remote source, log the raw string value.
5. Log the final typed resolution exactly once per `resolve()` call.
6. Cancellation is rethrown and is not logged as a failure.

## Test Contract

- A successful remote Treatment resolution logs fetch success, `source=remote`, `rawValue=promise_coach_v1`, and the typed Treatment result.
- A failed fetch with cached remote Control logs the failure and final readable Control result.
- A default-only value logs `source=default` and unreadable Control without a raw remote value.
- A cancelled fetch propagates cancellation and does not log a fetch failure or final resolution.
- The production `@Inject` constructor continues to accept only `FirebaseRemoteConfig` and delegates to `AppLogger.debug` internally. Tests use a separate internal constructor to inject a small logging function, avoiding any function-type binding requirement in Hilt while keeping tests independent of Android Logcat.

## Scope

Only the Firebase onboarding adapter and its focused unit test change. Remote Config behavior, assignment persistence, analytics, console setup, and release logging policy remain unchanged.
