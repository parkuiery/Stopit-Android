# Firebase A/B Testing Onboarding Assignment Design

## Goal

Replace the app-owned three-parameter rollout and random bucket assignment with one Firebase Remote Config experiment parameter. Firebase A/B Testing owns audience exposure, variant weights, and stable Firebase Installation ID assignment. The app owns only variant interpretation, safe loading, in-progress recovery, and analytics delivery.

This is a narrow assignment-boundary change. The existing Control and `PromiseCoachV1` screens, first-promise state machine, privacy contract, and analytics events remain unchanged.

## Decision

Use one string Remote Config parameter:

| Parameter | In-app default | Baseline | Treatment |
| --- | --- | --- | --- |
| `onboarding_variant` | `control` | `control` | `promise_coach_v1` |

Firebase A/B Testing configures the target audience, experiment exposure percentage, and baseline/treatment weights. The app must not generate a random bucket or interpret a percentage.

The following parameters are retired from the client contract:

- `onboarding_promise_coach_percent`
- `onboarding_promise_coach_new_assignment_enabled`
- `onboarding_promise_coach_emergency_disabled`

## Alternatives Considered

### Boolean parameter

`onboarding_promise_coach_enabled` would be the smallest representation for two paths. It was rejected because the domain and analytics already use named variants and a string parameter can add a future variant without changing the parameter type.

### Keep the three existing parameters

This preserves an app-owned percentage, new-assignment gate, and kill switch. It was rejected because Firebase A/B Testing already owns exposure and stable assignment, while the current asynchronous fetch can persist Control before Firebase has returned an experiment value.

### Remote Config Rollout instead of A/B Testing

A rollout is preferable when the only goal is staged operational release. It was not selected because this change explicitly targets an experiment comparing the existing and promise-coach onboarding using the existing activation funnel. The same single parameter remains compatible with a later rollout.

## Variant Resolution

`OnboardingExperimentConfig` exposes a suspending resolution operation rather than a synchronous snapshot. Its result is an `OnboardingExperimentResolution` with `variant: OnboardingVariant` and `remoteReadable: Boolean`; the Firebase adapter remains the only unit that interprets Remote Config source and string values.

Entry invokes this operation for both unassigned users and existing Treatment users. Existing Control users do not need a fetch to change routing because they must remain in Control for the rest of their in-progress onboarding.

The Firebase adapter:

1. Calls `fetchAndActivate()` before resolving a fresh assignment.
2. Reads `onboarding_variant` after the task completes.
3. Accepts only `control` and `promise_coach_v1` from a remote source.
4. On fetch failure, uses a previously activated remote value when it is valid.
5. When no valid activated remote value exists, returns an unreadable Control-safe result.
6. Maps unknown or malformed remote strings to an explicit readable Control result so an invalid console value cannot enable Treatment.

The Remote Config fetch timeout remains five seconds. No new dependency is added; the Firebase `Task` is adapted with the coroutine primitives already used in the app.

## Local State and Routing

Firebase owns experiment cohort assignment. `FirstPromiseDraftStore` continues to persist the resolved variant only to keep an onboarding already in progress stable across process death and system-settings round trips.

Entry behavior is:

- No local assignment + readable `promise_coach_v1`: persist `PromiseCoachV1` and route to `GoalSelect`.
- No local assignment + readable/default Control: persist Control and route to the existing `Intro`.
- Existing Control assignment: keep Control so a user is not moved between flows mid-onboarding.
- Existing Treatment + readable remote Control: apply the existing emergency recovery transition, then route using the recovered state. This makes stopping the experiment and publishing the baseline the single kill-switch path.
- Existing Treatment + fetch failure/unreadable result: preserve the local Treatment assignment. A transient network failure must not interrupt an active onboarding.
- Existing Treatment + readable Treatment: continue from the persisted phase.

The complete decision table is:

| Firebase result | No local assignment | Existing Control | Existing Treatment |
| --- | --- | --- | --- |
| Readable Treatment | assign Treatment | keep Control | continue Treatment |
| Readable Control | assign Control | keep Control | apply emergency recovery |
| Readable malformed value, mapped to Control | assign Control | keep Control | apply emergency recovery |
| Default-only / unreadable | assign Control | keep Control | preserve Treatment |
| Fetch failure + cached readable Treatment | assign Treatment | keep Control | continue Treatment |
| Fetch failure + cached readable Control | assign Control | keep Control | apply emergency recovery |
| Fetch failure without activated remote value | assign Control | keep Control | preserve Treatment |

The unreleased V1 local assignment format is retained; there is no production data migration. QA installations that already persisted the old Control assignment must clear app data once before validating the new experiment.

## Remote Config Initialization

The shared defaults map contains only:

```text
onboarding_variant = control
```

for onboarding assignment. The existing `in_app_review_enabled` default remains unchanged. Shared initialization may continue its eager refresh for non-onboarding consumers, but the onboarding resolver must await its own `fetchAndActivate()` result so assignment cannot race an unfinished fetch.

## Analytics and Experiment Setup

No new Analytics event is introduced. The experiment uses:

- Activation event: `onboarding_experiment_exposed`
- Firebase experiment primary metric: `app_block_intercepted`
- Detailed product success metric: among Treatment users with `first_promise_created`, the percentage reaching an attributable `app_block_intercepted` within 86,400 seconds
- Recommended guardrails: `first_lock_configured` as preparation only, crash-free users, terminal onboarding completion, and permission outcomes already documented in the funnel runbook

The activation event is emitted only when the assigned variant's first visible screen is actually shown: `Intro` for Control or `GoalSelect` for Treatment. Entry resolution and Remote Config activation must not emit exposure by themselves. The durable assignment must exist before the first visible screen records exposure, and its existing outbox ordering remains unchanged.

Firebase A/B Testing uses `app_block_intercepted` as the common comparable event. The ordered, attributable 24-hour Treatment metric remains the canonical decision metric in the funnel runbook and is evaluated through GA4/BigQuery rather than being misrepresented by the Firebase experiment's single-event primary metric. Firebase console creation, publishing, FID test-device targeting, and GA4 Admin registration remain external operational steps and are documented rather than reported as completed by source changes.

## Failure Safety

- Missing network or Firebase failure never enables Treatment for a new assignment.
- A transient fetch failure never ejects an already-started Treatment user.
- An invalid remote string fails closed to Control.
- Publishing `control` through the experiment/default template replaces the separate emergency flag on the next successful fetch.
- Corrupted local onboarding state retains the existing fail-closed corruption handling and is not overwritten by Remote Config.

## Verification

Tests must prove:

1. The Firebase adapter fetches before reading and maps both valid string variants.
2. Fetch failure falls back to a valid activated remote value.
3. Missing/default-only and malformed values fail closed.
4. The defaults map contains `onboarding_variant=control` and none of the retired keys.
5. Entry assigns a fresh user directly from the Firebase result without a bucket provider.
6. Existing Control remains stable when Treatment is fetched.
7. Existing Treatment survives an unreadable fetch result.
8. Existing Treatment applies emergency recovery when readable Control is fetched.
9. Entry resolution does not record exposure; the Control `Intro` and Treatment `GoalSelect` visible-screen paths each record it once.
10. Navigation, analytics ordering, JVM suites, lint, and a prod-like debug build remain green.

## Operational Handoff

After the app change ships to a QA build, Firebase console setup is:

1. Create Remote Config parameter `onboarding_variant` with default `control`.
2. Create a Remote Config A/B experiment for the intended Android app ID.
3. Set Baseline to `control` and Treatment to `promise_coach_v1`.
4. Use Firebase Installation ID targeting to force the QA device into Treatment and validate the complete flow.
5. Select `onboarding_experiment_exposed` as activation and `app_block_intercepted` as the Firebase primary goal; retain the ordered attributable 24-hour funnel as the product decision metric.
6. Publish only after reviewing the Remote Config template diff.
