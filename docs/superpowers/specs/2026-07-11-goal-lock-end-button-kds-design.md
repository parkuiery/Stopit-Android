# Goal Lock End Button KDS Alignment

## Problem

The goal-lock detail screen renders its destructive end action with a raw Material `OutlinedButton`. The KDS already defines the same semantic and visual treatment through `KeepButtonVariant.Destructive`, so the screen currently bypasses the design-system contract for height, shape, typography, colors, and disabled state.

## Design

Replace only the detail screen's bottom `Goal lock end` action with `KeepButton` using `KeepButtonVariant.Destructive`.

- Preserve the existing label, enabled/loading behavior, click callback, confirmation dialog, and end-goal business logic.
- Preserve the current full-width placement and surrounding spacing.
- Remove Material button imports that become unused.
- Do not add a goal-lock-specific button component or change KDS tokens.

## Verification

- Add or update a focused source/UI policy test if the existing test structure can assert KDS usage without brittle implementation inspection.
- Run the relevant goal-lock unit tests.
- Run `:app:lintDevDebug` to catch Compose/import/resource issues.
- Run `:app:assembleProdDebug` to validate the production-like artifact.

## Non-goals

- Redesigning the confirmation dialog.
- Changing termination behavior or analytics.
- Changing other destructive actions in the app.
- Modifying the KDS destructive button specification.
