# app selection boundary

## Purpose
App-level selectable-app repository and pure filtering policy shared by onboarding, Home, and Routine app pickers.

## Key Files
| File | Description |
|------|-------------|
| `InstalledAppRepository.kt` | PackageManager-backed scanner for selectable launchable apps; owns `QUERY_ALL_PACKAGES`/package-visibility sensitive framework access. |
| `SelectableAppPolicy.kt` | Pure JVM-testable filtering/sorting policy for selectable app candidates. |

## For AI Agents

### Working In This Directory
- Keep Android framework package queries inside `InstalledAppRepository`.
- Keep filtering/sorting rules in `SelectableAppPolicy` so QA can verify them without an emulator.
- Do not move this boundary back under a feature-private package; it is shared by onboarding, Home, and Routine app-selection UI.

### Testing Requirements
- `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.appselection.*'`
- `python3 -m unittest scripts.tests.test_shared_ui_component_boundaries -v`

### Common Patterns
- Repository code may depend on Android `PackageManager`; policy code should stay pure Kotlin/JVM-testable.
- UI packages should import this app-level boundary rather than feature-private repositories.
