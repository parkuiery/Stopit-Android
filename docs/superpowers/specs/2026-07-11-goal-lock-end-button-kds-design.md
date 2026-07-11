# Goal Lock End Button KDS Alignment

## Problem

The goal-lock detail screen renders its destructive end action with a raw Material `OutlinedButton`. The KDS already defines the same semantic and visual treatment through `KeepButtonVariant.Destructive`, so the screen currently bypasses the design-system contract for height, shape, typography, colors, and disabled state.

## Design

Replace only the detail screen's bottom `Goal lock end` action with `KeepButton` using `KeepButtonVariant.Destructive` and `bottomSpacing = false`.

- Preserve the existing label, click callback, confirmation dialog, and end-goal business logic.
- Keep the button conditionally rendered inside the existing `if (state.canEnd)` branch. It remains enabled whenever rendered; no loading label or disabled state is introduced.
- Preserve the current full-width placement. Disable `KeepButton`'s built-in bottom spacing so the existing trailing `24.dp` spacer remains the single source of bottom spacing.
- Remove Material button imports that become unused.
- Do not add a goal-lock-specific button component or change KDS tokens.

## Verification

- No source-inspection test will be added for the visual-only component substitution; that would couple tests to Compose implementation details without proving rendering.
- Run `GoalLockDetailViewModelTest` and `GoalLockDetailPresentationTest` to confirm the existing visibility and termination-state policies remain intact.
- Run `:app:lintDevDebug` to catch Compose/import/resource issues.
- Run `:app:assembleProdDebug` to validate the production-like artifact.

## Non-goals

- Redesigning the confirmation dialog.
- Changing termination behavior or analytics.
- Changing other destructive actions in the app.
- Modifying the KDS destructive button specification.
