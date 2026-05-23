<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# main source set

## Purpose
Primary Android source set containing the manifest, Kotlin application code, resources, launcher artwork, services, receivers, and Compose UI.

## Key Files
| File | Description |
|------|-------------|
| `AndroidManifest.xml` | Main Android manifest declaring app components, services, permissions, and metadata. |
| `ic_launcher-playstore.png` | Play Store launcher artwork. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `java/` | Kotlin package root for the Android application code. (see `java/AGENTS.md`) |
| `res/` | Android resource tree for drawables, launcher icons, Lottie/raw animations, localized strings, themes, colors, and service/backup XML. Do not place `AGENTS.md` inside Android `res/`; Gradle packages files there as resources. |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew :app:testDevDebugUnitTest
- ./gradlew :app:assembleProdDebug
- ./gradlew :app:connectedDevDebugAndroidTest when Android services/receivers/permissions/resources require device validation.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
