<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-27 | Updated: 2026-04-27 -->

# documentation

## Purpose
Project documentation for workflow, plans, and historical design/spec artifacts.

## Key Files
| File | Description |
|------|-------------|
| `GIT_WORKFLOW.md` | Git branching, commit, and release workflow documentation. |

## Subdirectories
| Directory | Purpose |
|-----------|---------|
| `superpowers/` | Superpowers-generated implementation planning and specification artifacts. (see `superpowers/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Keep changes scoped to this directory’s responsibility and follow the neighboring file naming/style conventions.

### Testing Requirements
- ./gradlew test for repository-wide JVM tests when behavior changes.

### Common Patterns
- Kotlin files use package paths that match directory structure.
- Prefer existing app/KDS utilities before adding new abstractions or dependencies.

## Dependencies

### Internal
- See parent AGENTS.md for broader module dependencies.

### External
- See module build files for dependency declarations.

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
