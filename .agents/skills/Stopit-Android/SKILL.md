# Stopit-Android Development Patterns

> Repo-local ECC skill for AI agents working in `parkuiery/Stopit-Android`.

## Overview

Stopit is a Kotlin Android screen-time management app. The main application module is `:app` under package `com.uiery.keep`; shared Compose design-system components live in `:core:kds`. The app uses Jetpack Compose, Orbit MVI, Hilt, Room, DataStore, Firebase Analytics/Crashlytics/Messaging, and Android services/receivers for app-blocking behavior.

Use this skill together with the root `AGENTS.md` and the nearest nested `AGENTS.md` for the files being changed.

## Repository Boundaries

- Keep app-specific feature orchestration in `app/`.
- Put reusable visual primitives and theme-level UI components in `core/kds/`.
- Do not commit local secrets, `local.properties` changes, generated build output, or flavor `google-services.json` edits unless explicitly requested.
- For Play deploy or release-secret work, treat `docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md` as the source of truth before changing helpers, workflows, or docs.
- Prefer existing app and KDS utilities before adding new abstractions or dependencies.

## Architecture Patterns

### Feature Structure

Feature code generally lives under `app/src/main/java/com/uiery/keep/feature/<feature>/` and commonly includes:

- `<Feature>Screen.kt` for Compose UI
- `<Feature>ViewModel.kt` for Orbit MVI state management
- `<Feature>Navigation.kt` for type-safe navigation routes
- `component/` for feature-specific composables

### State Management

- ViewModels use Orbit MVI via `ContainerHost`.
- UI state should be represented with data classes.
- One-time events should be represented as sealed side effects.
- Mutate state inside `intent { reduce { ... } }` and emit one-time events with `postSideEffect(...)`.

### Data and Runtime Services

- Room database code lives under `app/src/main/java/com/uiery/keep/database/`.
- DataStore preference keys live in `datastore/PreferencesKey.kt`.
- Firebase, Crashlytics, Analytics, and FCM remain in use; the first-party Retrofit/OkHttp backend API layer has been removed.
- Accessibility service, receivers, alarm/exact-alarm behavior, emergency unlock, and notification flows often require Android runtime or instrumentation validation beyond unit tests.

## Build Variants and Verification

This repo defines `dev` and `prod` flavors. Do not use flavor-less Gradle tasks such as `testDebugUnitTest`, `lintDebug`, or `assembleDebug` as default guidance because they are ambiguous.

Use these commands by default:

```bash
./gradlew :app:testDevDebugUnitTest
./gradlew :app:assembleProdDebug
```

Use release-path checks when release behavior, minification, Play deploy, secrets, or prod-only configuration is affected:

```bash
./gradlew :app:testProdReleaseUnitTest :app:bundleProdRelease
```

Use Android instrumentation when framework behavior, Room migrations, services, receivers, accessibility, alarms, or notifications are affected:

```bash
./gradlew :app:connectedDevDebugAndroidTest
```

## Change Guidelines

- Kotlin package paths should match directory structure.
- Prefer small, focused changes tied to one issue or product/quality slice.
- Keep generated files and build artifacts out of source and docs.
- When modifying user-visible strings, check locale/string parity rather than updating only one locale.
- When modifying Firebase, Play Deploy, Crashlytics, GA4, or release workflows, update or verify the related runbook/docs in the same change when the contract changes.
- For UI work, reuse KDS components and theme tokens where possible.
- For runtime gates and permissions, verify both code paths and user-facing fallback behavior.

## Useful Review Checklist

Before opening or merging a PR, check:

- Does the change respect the `:app` vs `:core:kds` boundary?
- Are Gradle commands flavor-qualified?
- Are secrets and generated artifacts excluded?
- Are tests appropriate for the changed layer?
- Do docs/runbooks match workflow or release-contract changes?
- Are Crashlytics/Analytics/Play Deploy assumptions backed by code, docs, or console evidence?
