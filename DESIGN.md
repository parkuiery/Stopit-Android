# DESIGN.md

## Product Design Intent

Keep is a focused Android app for app blocking, timed focus sessions, routines, lock history, and emergency unlock. The interface should feel calm, direct, and operational. Preserve the current screen flow and visual behavior unless a change is explicitly justified by maintainability, consistency, accessibility, or testability.

This document is the UI contract for coding and design agents. It follows the plain-markdown DESIGN.md approach referenced by `https://github.com/VoltAgent/awesome-design-md`, but it describes Keep's existing Android design language rather than copying another brand.

## Non-Negotiables

- Do not redesign the app visually for its own sake.
- Do not change screen flow, major copy meaning, lock policy, or persisted data meaning without explicit approval.
- Keep UI changes traceable to `core/kds` tokens/components and this document.
- Prefer existing KDS components before introducing new feature-local UI patterns.
- Do not add new UI dependencies unless explicitly approved.

## Platform

- Android app built with Jetpack Compose and Material 3.
- Root app theme is `KeepTheme`.
- Shared UI module is currently `core:kds`; the module may be renamed later, but design tokens must stay centralized.
- Primary font is Pretendard from KDS font resources.

## Color System

Use `KeepTheme.colors` instead of raw colors in app UI.

### Brand And Status

- `primary`: `#FFA927` in light and dark. Use for primary actions, selected states, active indicators, emphasis, and progress.
- `error`: `#F04452` in light and dark. Use for destructive, warning, or emergency states.

### Backgrounds

- `background`: screen background.
  - Light: `#FFFFFF`
  - Dark: `#17171C`
- `onBackground`: dim/scrim overlay.
  - Light: `#17171C`
  - Dark: `#8E000000`
- `secondary`: subtle container background.
- `onSecondary`: elevated card/sheet background.
- `tertiary`: grouped block, chip, or control background.

### Text And Icons

- `onSurfaceVariant`: primary title/body text.
- `surfaceVariant`: secondary or supporting text.
- `onSurface`: tertiary text and secondary icons.
- `surface`: low-emphasis helper text.
- `onTertiaryContainer`: disabled or placeholder text/icon.

## Typography

KDS defines a full Material typography scale with Pretendard:

| Token | Weight | Size | Line height | Usage |
| --- | --- | ---: | ---: | --- |
| `displayLarge` | Bold | 57sp | 64sp | Avoid in normal app screens. |
| `displayMedium` | Bold | 45sp | 52sp | Avoid in normal app screens. |
| `displaySmall` | Bold | 36sp | 44sp | Large empty/celebration states only. |
| `headlineLarge` | Bold | 32sp | 40sp | Prominent bottom-sheet titles. |
| `headlineMedium` | SemiBold | 28sp | 36sp | Feature section emphasis. |
| `headlineSmall` | SemiBold | 24sp | 32sp | Screen-level headings when needed. |
| `titleLarge` | SemiBold | 22sp | 28sp | Onboarding and setup titles. |
| `titleMedium` | Medium | 16sp | 24sp | Card titles, row labels. |
| `titleSmall` | Medium | 14sp | 20sp | Compact labels. |
| `bodyLarge` | Normal | 16sp | 24sp | Main body text. |
| `bodyMedium` | Normal | 14sp | 20sp | Supporting body text. |
| `bodySmall` | Normal | 12sp | 16sp | Captions and metadata. |
| `labelLarge` | Medium | 14sp | 20sp | Buttons and tabs. |
| `labelMedium` | Medium | 12sp | 16sp | Chips and compact labels. |
| `labelSmall` | Medium | 11sp | 16sp | Dense metadata. |

Do not introduce viewport-scaled type. Keep letter spacing at existing typography token values unless changing KDS itself.

## Shape And Spacing

Current app surfaces use compact rounded corners:

- 4dp: small day chips and dense selection markers.
- 6dp: tiny status badges.
- 8dp: segmented controls, calendar cells, picker surfaces.
- 10dp: compact app rows.
- 12dp: default cards, app items, category buttons, primary buttons.
- 20dp: rounded text fields.
- Circle: snackbars and fully round badges.

Spacing should stay on the existing Compose `dp` scale already present in screens. When extracting shared components, prefer named KDS defaults over feature-local magic numbers.

## Components

### KeepTheme

Wrap app UI in `KeepTheme`. Access colors through `KeepTheme.colors`. Material typography is supplied by KDS `Typography`.

### KeepButton

Primary action button.

- Shape: 12dp.
- Container: `primary`.
- Text: white, bold, 18sp.
- Padding: 18dp vertical, 24dp horizontal.
- Default bottom padding: 24dp.
- Use for single primary actions such as continue, start, save, and confirm.

### KeepCheckbox

Use for binary selections when checkbox semantics are expected. Keep checked/unchecked color behavior centralized in KDS.

### KeepSnackBar

Use for transient feedback. Shape is circular, background is `onSecondary`, text is `onSurfaceVariant`.

### KeepModalBottomSheet

Use for bottom-sheet flows that need system bar color coordination. Default container is `onSecondary`; drag handle uses `tertiaryContainer`.

### KeepBannerAd

Use for AdMob banner surfaces. Do not move ad behavior into generic layout components.

### RotatingCircleGradient

Use for existing circular progress/emphasis motion. Do not introduce decorative gradient/orb backgrounds.

## Screen Patterns

### Onboarding

- Keep current step-by-step flow.
- Use `background` for full-screen surface.
- Titles generally map to `titleLarge` or equivalent existing style.
- Primary CTA should use `KeepButton`.

### Home And Lock

- Preserve timer/category/lock semantics.
- Use `primary` for active lock/focus emphasis.
- Use KDS or documented component patterns for repeated picker, category, and app-row surfaces.

### Routine

- Preserve local Room plus `RoutineScheduler` behavior.
- Use consistent 12dp cards for routine rows.
- Selected day/time controls should keep current compact, direct interaction style.

### History And Lock History

- Use dense but readable cards and list rows.
- Preserve calendar/tab behavior.
- Use `surfaceVariant` and `onTertiaryContainer` for secondary metadata.

### Menu And Devtool

- Keep utilitarian list-row styling.
- Devtool may expose technical values, but visual treatment should still use KDS typography/colors.

## Accessibility

- Preserve semantic roles for buttons, switches, checkboxes, tabs, and selectable rows.
- Keep touch targets practical for repeated actions.
- Ensure disabled states are visually distinct through `tertiaryContainer` and `onTertiaryContainer`.
- Do not encode critical state using color alone.
- Keep text readable in light and dark themes.

## Compose Implementation Rules

- Prefer KDS components and tokens before feature-local styling.
- Feature-local components are acceptable when reuse is not real yet.
- Extract to KDS only when a pattern appears in multiple places or the extraction clarifies behavior.
- Avoid nested cards and decorative wrappers.
- Keep UI state in ViewModels or local state according to existing Orbit MVI patterns.
- Do not change navigation routes or side-effect semantics during visual cleanup.

## Documentation Rules

- Update this file when tokens, reusable components, or screen-level conventions change.
- Keep `core/kds/README.md` aligned with this document and the actual KDS source.
- When a UI phase intentionally improves accessibility or consistency, record why the visible change is acceptable.
