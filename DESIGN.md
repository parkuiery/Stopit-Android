# DESIGN.md

## Source of truth

- Status: Active
- Last refreshed: 2026-07-29
- Primary product surfaces: onboarding, home/focus timer, routine, lock, emergency unlock, history, menu
- Evidence reviewed: `core/kds`, app Compose screens, accessibility tests, and SEED foundations/component guidance
- KDS implementation guides: [`core/kds/docs`](core/kds/docs/README.md). Every reusable
  `Keep*.kt` component has a local guide that links the current SEED source and records usage,
  anatomy, properties/states, StopIt adaptations, accessibility, and verification.
- SEED references: [color roles](https://seed-design.io/foundations/color/color-role),
  [inclusive design](https://seed-design.io/foundations/inclusive-design),
  [action button](https://seed-design.io/components/action-button),
  [top navigation](https://seed-design.io/components/top-navigation),
  [bottom sheet](https://seed-design.io/components/bottom-sheet), and
  [alert dialog](https://seed-design.io/components/alert-dialog)

## Brand

- Personality: calm, direct, trustworthy, and operational.
- Trust signals: predictable lock state, clear remaining time, explicit emergency actions, and readable status feedback.
- Avoid: decorative gradients, excessive brand yellow/amber, borrowed marketplace branding, and ambiguous controls.

## Product goals

- Goals: make starting, understanding, and ending a focus commitment fast and unambiguous.
- Non-goals: redesigning navigation, lock policy, or persisted behavior as part of design-system work.
- Success signals: fewer feature-local style overrides, accessible state communication, and reusable KDS components.

## Personas and jobs

- Primary personas: people reducing distracting app use and people maintaining recurring focus routines.
- User jobs: select blocked apps, start a timed lock, understand protection status, configure routines, and recover safely.
- Key contexts of use: quick setup, repeated daily use, time pressure, and accessibility services enabled.

## Information architecture

- Primary navigation: onboarding into home, with routine, history, and menu destinations.
- Core routes/screens: onboarding, home, app selection, routine, lock, emergency unlock, history, and settings.
- Content hierarchy: protection state and primary action first; configuration second; metadata and diagnostics last.

## Design principles

- Preserve product behavior while centralizing reusable visual decisions.
- Use one high-emphasis action per surface and communicate state with more than color.
- Prefer semantic tokens and component APIs over raw values or Material slot repurposing.
- SEED is the KDS foundation and component specification; Keep-specific flows and content remain product-owned.

## Product Design Intent

Keep is a focused Android app for app blocking, timed focus sessions, routines, lock history, and emergency unlock. The interface should feel calm, direct, and operational. Preserve the current screen flow and visual behavior unless a change is explicitly justified by maintainability, consistency, accessibility, or testability.

This document is the UI contract for coding and design agents. KDS follows SEED's published
palette, semantic roles, component variants, dimensions, typography scale, and interaction
states while retaining Keep's Android architecture and product behavior.

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
- Typography uses Android's system font, following SEED's platform-native font policy.

## Visual language

### Color System

New KDS components use `KeepTheme.semanticColors` instead of raw colors or generic Material
slots. `KeepTheme.colors` remains a compatibility API while existing screens migrate.

### Brand And Status

- KDS follows SEED's semantic role and component-state structure, but never copies SEED's
  Carrot palette. StopIt owns the brand palette.
- `background.brandSolid`: StopIt amber (`#FFA927`) in light mode and bright StopIt amber
  (`#FFB84A`) in dark mode.
- `background.brandSolidPressed`: `#F09300` in light mode and `#FFD27A` in dark mode.
- `foreground.brand`: contrast-safe StopIt amber-brown (`#A85D00`) in light mode and
  `#FFB84A` in dark mode.
- Brand Solid labels and icons use static-white `foreground.onBrand`, following the product's
  established CTA direction. Keep this pairing on large, bold primary controls and status
  elements; do not use StopIt amber as a background for small or body-weight white text.
- Critical roles use SEED's theme-aware red palette for destructive, error, and emergency states.
- Semantic roles are grouped as foreground, background, and stroke, with neutral, brand,
  critical, disabled, layer, solid, and weak intents.

### Primary Color Hierarchy

Primary is a scarce emphasis token, not the default icon color. A screen should make the highest-value action or active state obvious without making every amber element look equally important.

Use `primary` for:

- the single primary CTA on a screen or sheet, such as start, save, continue, confirm, or selection complete;
- current selection states, such as selected tab, selected day, selected chip, or active filter;
- active lock/focus/routine state, countdown/progress, and status that tells the user Stopit is currently protecting their intent;
- important achievement/progress numbers when they represent product value delivered, not generic metadata.

Avoid `primary` for:

- TopAppBar back, menu, close, and other navigation icons;
- secondary icon-only actions such as generic edit/delete unless the screen has deliberately promoted that action as its primary action;
- helper text, captions, metadata, or decorative emphasis;
- destructive or emergency meanings, which should use `error` or a confirmation pattern instead of brand amber.

If a selected/active state uses `primary`, do not rely on color alone. Pair it with at least one non-color cue: text, badge, chip/container shape, border, content description, or clear layout position.

The detailed #468 audit and follow-up checklist live in `docs/DESIGN_PRIMARY_COLOR_HIERARCHY.md`.

### Backgrounds

- `background`: SEED layer basement.
  - Light: gray-200 (`#F3F4F5`)
  - Dark: gray-00 (`#000000`)
- Modal scrim: static-black-alpha-700 (`#74000000`).
- `secondary`: subtle container background.
- `onSecondary`: elevated card/sheet background.
- `tertiary`: grouped block, chip, or control background.

### Text And Icons

- `onSurfaceVariant`: primary title/body text.
- `surfaceVariant`: secondary or supporting text.
- `onSurface`: tertiary text and secondary icons.
- `surface`: low-emphasis helper text.
- `onTertiaryContainer`: disabled or placeholder text/icon.

### Typography

KDS maps Material text roles onto SEED's t1–t14 system-font scale:

| Token | Weight | Size | Line height | Usage |
| --- | --- | ---: | ---: | --- |
| `displayLarge` | Bold | 48sp | 60sp | Avoid in normal app screens. |
| `displayMedium` | Bold | 40sp | 52sp | Avoid in normal app screens. |
| `displaySmall` | Bold | 32sp | 42sp | Large empty/celebration states only. |
| `headlineLarge` | Bold | 28sp | 38sp | Prominent bottom-sheet titles. |
| `headlineMedium` | Bold | 26sp | 35sp | Feature section emphasis. |
| `headlineSmall` | Bold | 24sp | 32sp | Screen-level headings when needed. |
| `titleLarge` | Bold | 22sp | 30sp | Onboarding and setup titles. |
| `titleMedium` | Bold | 18sp | 24sp | Card titles, row labels. |
| `titleSmall` | Bold | 16sp | 22sp | Compact labels. |
| `bodyLarge` | Normal | 16sp | 22sp | Main body text. |
| `bodyMedium` | Normal | 14sp | 19sp | Supporting body text. |
| `bodySmall` | Normal | 12sp | 16sp | Captions and metadata. |
| `labelLarge` | Bold | 14sp | 19sp | Buttons and tabs. |
| `labelMedium` | Bold | 13sp | 18sp | Chips and compact labels. |
| `labelSmall` | Bold | 11sp | 15sp | Dense metadata. |

Do not introduce viewport-scaled type. Keep letter spacing at existing typography token values unless changing KDS itself.

### Shape And Spacing

KDS uses SEED's radius scale:

- 4dp: small day chips and dense selection markers.
- 6dp: tiny status badges.
- 8dp: segmented controls, calendar cells, picker surfaces.
- 10dp: compact app rows.
- 12dp: default cards, app items, category buttons, primary buttons.
- 20dp: rounded text fields.
- 24dp: modal bottom-sheet top corners.
- Full: XSmall buttons and fully round badges.

Spacing should stay on the existing Compose `dp` scale already present in screens. When extracting shared components, prefer named KDS defaults over feature-local magic numbers.

## Components

### Ownership boundary

KDS owns every reusable component that carries visual policy, interaction-state styling, or
accessibility defaults. App screens compose KDS components and must not configure raw Material
components to recreate a local visual variant.

Direct Material use in app screens is limited to:

- layout and host primitives without product styling: `Scaffold`, `Text`, `Icon`, `Surface`;
- state holders and infrastructure: `SnackbarHostState`, sheet/date/time picker state.

The following visual components must be consumed through KDS:

| UI need | KDS component | SEED reference |
| --- | --- | --- |
| Primary, secondary, outline, destructive, ghost action | `KeepButton` / `KeepTextButton` | Action Button |
| Icon-only action | `KeepIconButton` | Action Button icon-only layout |
| Contextual action list | `KeepMenu`, `KeepMenuItem` | Menu |
| Content surface | `KeepCard` | Layer and interactive surface roles |
| Radio-style selectable surface | `KeepSelectableCard` | Radio Select Box |
| App/screen navigation header | `KeepTopAppBar`, `KeepCenterAlignedTopAppBar` | Top Navigation |
| Binary and exclusive selection | `KeepCheckbox`, `KeepSwitch`, `KeepRadioButton` | Checkbox, Switch, Radio |
| Compact action, toggle, or filtering | `KeepChip` | Chip |
| Compact mutually exclusive view switch | `KeepSegmentedControl` | Segmented Control |
| Small property or status label | `KeepBadge` | Badge |
| Field, metadata, and section label | `KeepLabel` | Text roles |
| Single-line text entry | `KeepTextField` | Text Input |
| Picker or selection entry | `KeepInputButton` inside `KeepField` | Input Button |
| Date and time entry | `KeepDatePickerDialog`, `KeepTimeInput` | Date/Time Input shell |
| Confirmation or destructive interruption | `KeepAlertDialog`, `KeepConfirmationDialog` | Alert Dialog |
| Loading and determinate progress | `KeepCircularProgressIndicator`, `KeepLinearProgressIndicator` | Progress Circle |
| Section separation | `KeepDivider` | Divider |
| Transient feedback | `KeepSnackbarHost`, `KeepSnackBar` | Snackbar |
| Modal bottom content | `KeepModalBottomSheet` | Bottom Sheet |

KDS components may internally use stable Material/Compose primitives for semantics, focus,
TalkBack, ripple, and platform integration. That implementation detail must not leak theme
slots or `*Defaults` objects into app feature APIs.

### KeepTheme

Wrap app UI in `KeepTheme`. Access new component colors through
`KeepTheme.semanticColors`. Material typography is supplied by KDS `Typography`.

New KDS components must use `KeepTheme.semanticColors`. Material components receive a proper
KDS `MaterialTheme.colorScheme`; `KeepTheme.colors` is limited to compatibility with existing
screen styling.

### KeepButton

SEED Action Button implementation.

- Variants: Brand Solid, Neutral Solid, Neutral Weak, Brand Outline, Neutral Outline,
  Critical Solid, and Ghost.
- Sizes: XSmall (32dp), Small (36dp), Medium (40dp), and Large (52dp).
- Brand Solid uses `background.brandSolid` and static-white `foreground.onBrand` content.
- Pressed colors and scale follow each SEED size/variant state.
- Disabled and loading states use semantic SEED roles.
- Default bottom padding: 24dp.
- Use for single primary actions such as continue, start, save, and confirm.

### Action hierarchy

- `BrandSolid`: one per screen at most; reserve for the product's highest-value action.
- The Home app-selection entry is a `NeutralMuted` KDS card. It is a configuration entry
  rather than the screen's primary action, so it retains the established neutral gray
  hierarchy. Its container uses gray-400 in both themes to remain distinct from the screen
  basement. Keep the shield asset's original blue-gray multi-tone colors; do not flatten it
  with the card's black content color. The trailing affordance uses a subtle neutral role.
  When editing is locked, the card uses the KDS disabled container and foreground roles.
- `NeutralSolid`: primary CTA when brand emphasis would distract from content.
- `NeutralWeak`: common secondary action and paired dismiss action.
- `BrandOutline` / `NeutralOutline`: low-emphasis alternatives; do not pair with Solid
  variants in the same horizontal group.
- `CriticalSolid`: irreversible or high-consequence confirmation only.
- `Ghost` / `KeepTextButton`: toolbar and inline actions with no container emphasis.
- Icon-only actions require a localized `contentDescription` on their icon and retain a
  minimum 48dp touch target even when the glyph is smaller.

### Cards and layers

- `KeepCard` defaults to `background.layerDefault`, 12dp radius, no elevation, and an
  optional `stroke.neutralWeak` border.
- Use `LayerDefault` for normal grouped content, `NeutralWeak` for secondary controls,
  `NeutralMuted` when a neutral control needs separation from a neutral basement,
  `BrandWeak` for selected/brand context, and `CriticalWeak` for recoverable warnings.
- `KeepSelectableCard` owns radio semantics, selected background, indicator, title,
  description, disabled state, and minimum touch behavior.
- A clickable card must expose click semantics through `KeepCard(onClick=…)`; do not apply
  `Modifier.clickable` to a decorative container.
- Avoid nested cards. Use spacing and `KeepDivider` to express hierarchy within one surface.

### Dialogs

- `KeepAlertDialog` follows SEED Alert Dialog: overlay plus `layerFloating`, 272dp maximum
  width, 20dp radius, 20dp header/footer horizontal padding, and an 8dp action gap.
- Confirmation dialogs use two actions or fewer. The default pair is `NeutralWeak` dismiss
  plus `NeutralSolid` confirm; reserve `BrandSolid` for product-core permission or protection
  actions and `CriticalSolid` for irreversible or destructive confirmation.
- Action buttons are horizontal by default and stack vertically when localized labels do not
  fit. Alert dialogs do not dismiss from an outside tap; users make an explicit choice.
- Alert content is limited to a title and supporting description. Wheel pickers and other
  structured interactive content use `KeepDialog`, which shares the floating layer, width,
  and radius without pretending to be an alert.
- App code must not import Material `AlertDialog` or Compose `Dialog` directly.

### Chips, badges, and labels

- `KeepChip` follows SEED's `Solid`, `OutlineStrong`, and `OutlineWeak` variants with
  `Small`, `Medium`, and `Large` sizes. Toggle and radio use cases must expose their selected
  state and semantic role; screen code owns only the value and event.
- Use `KeepChip`, not feature-local pill-shaped `Box` implementations, for presets, duration
  choices, filters, and compact selection.
- `KeepBadge` expresses an object's property or state. Supported tones are `Neutral`, `Brand`,
  and `Critical`; supported variants are `Weak`, `Solid`, and `Outline`.
- Badges are display-only and must not be clickable. Status meaning must also be available in
  text or semantics rather than color alone.
- `KeepLabel` owns repeated small-text hierarchy. Use `Neutral` for primary labels, `Muted`
  for captions/metadata, `Brand` for selected values, and `Critical` for validation or risk.
- Product-specific card and modal composables may remain in `:app`, but their surfaces,
  labels, badges, chips, actions, and progress indicators must be KDS components.

### Navigation

- Screen-level top bars use `KeepTopAppBar`; centered titles use
  `KeepCenterAlignedTopAppBar`.
- Navigation icons use `KeepIconButton`. Back, close, menu, and overflow actions use neutral
  foreground, never brand color by default.
- All screen-level top bars use `background.layerBasement` so the header continues the
  screen canvas without an unrelated white band. Cards and grouped content provide the
  `layerDefault` separation. Title text uses `foreground.neutral`.

### Form controls

- `KeepField` owns the SEED Header/Input/Footer structure: label and requirement mark,
  input slot, helper/error precedence, and character count. Labels are noun phrases and are
  not replaced by placeholders.
- `KeepTextInput` owns the value shell, container, stroke, cursor, placeholder,
  prefix/suffix, clear action, disabled/read-only and focus states. Focus uses
  `stroke.neutralContrast`, not the StopIt brand color; error uses `stroke.criticalSolid`.
- `KeepTextField` composes `KeepField` and `KeepTextInput`. Use `Outline` (52dp mobile large)
  in forms with multiple inputs and `Underline` (40dp) when the screen or sheet has one text
  input. Material `TextField`, `OutlinedTextField`, and feature-local `BasicTextField`
  decoration are not app-level APIs.
- `KeepRadioButton`, `KeepCheckbox`, and `KeepSwitch` own all selected/unselected/disabled
  colors. Feature code must not pass Material color objects.
- `KeepSwitch` follows SEED Switchmark geometry: `52×32` with a 26dp thumb by default,
  full-radius track/thumb, no outline, a white thumb in both states, and 38% opacity when
  disabled. Small (`26×16`) and Medium (`38×24`) variants retain a 48dp touch target.
  Selected tracks use StopIt's `background.brandSolid`; unselected tracks use the neutral
  gray-600 role.
- Field and section captions use `KeepLabel`; do not repeat local font-size/color recipes.
- Validation text belongs next to the field and must not rely on a red outline alone.

### Feedback and progress

- Dialog structure and action hierarchy follow the `Dialogs` section above. Do not create a
  second dialog radius or action rule at a feature call site.
- `KeepSnackbarHost` always renders `KeepSnackBar`; screens own only placement and host state.
- Progress indicators use KDS semantic colors and retain progress semantics. Loading buttons
  use `KeepButton(loading = true)` instead of replacing the whole action with a raw spinner.
- `KeepDivider` uses `stroke.neutralWeak`; feature code changes spacing, not divider color.

### KeepCheckbox

Use for binary selections when checkbox semantics are expected. Keep checked/unchecked color behavior centralized in KDS.

### KeepSnackBar

Use for transient feedback. Shape is 8dp, background is `background.neutralInverted`, and
text is `foreground.inverted`.

### KeepModalBottomSheet

Use for bottom-sheet flows. The StopIt adaptation uses `background.layerSheet`
(`#F7F8F9` light, `#1D2025` dark) rather than the pure-white dialog surface so a large sheet
does not read as a bright blank page. The top radius is 24dp, tonal elevation is 0dp, the
scrim uses `background.overlay`, and the drag handle uses `stroke.neutralWeak`. Dialogs keep
using `background.layerFloating`; do not share the sheet override with them. The visible
handle remains compact but its interaction area is at least 44dp.
- Screen code may provide sheet state, a KDS-composed handle/header, and content. Shape,
  layer colors, scrim, tonal elevation, maximum width, and insets stay KDS-owned.

### AdMob / monetization boundary

KDS does not own AdMob runtime behavior. Banner ad SDK lifecycle and monetization analytics live in the app analytics/monetization boundary (`TrackedBannerAd`).

### RotatingCircleGradient

Use for existing circular progress/emphasis motion. Do not introduce decorative gradient/orb backgrounds.

## Screen Patterns

### Onboarding

- Keep current step-by-step flow.
- Use `background` for full-screen surface.
- Titles generally map to `titleLarge` or equivalent existing style.
- Primary CTA should use `KeepButton`.
- In a bottom action region with both primary and secondary actions, place the secondary action first and the primary CTA last, closest to the bottom safe area.
- When a proposal summary has two or three peer edit actions, group them in one compact horizontal row when localized labels fit. Use neutral outlined containers with at least 48dp touch height so the primary color remains reserved for the forward CTA.

### Home And Lock

- Preserve timer/category/lock semantics.
- Use `primary` for active lock/focus emphasis.
- Use KDS or documented component patterns for repeated picker, category, and app-row surfaces.
- Keep the lock-screen banner in a dedicated full-width bottom slot immediately above the system safe area, with protection status and emergency actions above it.

### Routine

- Preserve local Room plus `RoutineScheduler` behavior.
- Use `KeepCard` with `LayerDefault`, a 12dp radius, and `stroke.neutralWeak` for routine
  rows so they remain distinct from the `layerBasement` screen canvas. Keep enabled and
  disabled rows on the same structural surface; communicate routine state through the
  status badge, switch, and semantic foreground roles instead of making disabled rows more
  visually prominent.
- The create and edit surfaces share one routine editor. It follows the SEED Bottom Sheet
  anatomy: left-aligned title/description and close action, scrollable form content, and a
  fixed footer CTA. Because it is a multi-step form, it omits the drag handle and keeps
  the header dedicated to closing the editor.
- Routine editor fields use `KeepField` consistently. App, time, and protection values open
  another selection surface through `KeepInputButton`; only the routine name uses
  `KeepTextField`.
- Repeat days use an `OutlineWeak` multi-select `KeepChip` group. Keep every selected state
  available through toggle semantics rather than color alone.
- Time pickers keep changes local until the user selects Apply, and time validation is shown
  beside the schedule field. Protection is optional and is represented as a picker value,
  not as a switch, because the value is persisted only when the footer action is submitted.
- Routine protection helper copy uses `KeepFieldHelperTone.Muted` because it explains editing
  and deletion constraints; generic instructional helper copy remains `Subtle`.
- Destructive actions must not appear as a direct icon beside the close action or as a form
  field near the fixed submit footer. Edit mode places a neutral overflow trigger before the
  close action; its SEED Menu contains a Critical `Delete routine` item and opens a Critical
  confirmation dialog that names the routine and explains that deletion cannot be undone.
- The body scrolls independently when content exceeds the available height. Header and
  footer remain visible, and the footer moves above the IME while editing the name.

### History And Lock History

- Use dense but readable cards and list rows.
- Preserve calendar/tab behavior.
- Use `foreground.neutral` for titles, dates, values, and app names.
- Use `foreground.muted` for supporting copy, field labels, session types, counts, and
  empty-state guidance. Do not use legacy `onTertiaryContainer`, placeholder/disabled roles,
  or background roles for readable history metadata.
- Keep compact calendar and ranking text at `bodySmall`/`labelSmall` or larger so supporting
  information remains readable under system font scaling.

### Menu And Devtool

- Keep utilitarian list-row styling.
- Menu toggle titles use `foreground.neutral`; subtitles that explain protection behavior use
  `foreground.muted`, not disabled/placeholder legacy colors.
- Devtool may expose technical values, but visual treatment should still use KDS typography/colors.

## Accessibility

- Preserve semantic roles for buttons, switches, checkboxes, tabs, and selectable rows.
- Target at least 48dp touch areas for primary controls and practical touch areas for repeated actions.
- Ensure disabled states are visually distinct through semantic disabled foreground/background roles.
- Do not encode critical state using color alone.
- Keep text readable in light and dark themes.
- KDS contract tests must lock StopIt's brand/on-brand values and the light/dark semantic roles.
- Respect system font scaling and avoid fixed-height text containers that clip localized content.

## Responsive behavior

- Supported devices: Android phones first; larger widths must not stretch readable content without bounds.
- Layout adaptations: allow button groups and dense controls to wrap or stack when text grows.
- Touch/hover differences: touch and TalkBack are primary; hover must never be the only state signal.

## Interaction states

- Loading: preserve labels or provide a clear progress semantic; prevent duplicate actions.
- Empty: explain the missing content and provide the next available action.
- Error: place actionable guidance near the failing control and announce important changes.
- Success: confirm completion without relying on green or motion alone.
- Disabled: use semantic disabled colors and keep the reason understandable from surrounding copy.
- Offline/slow network: retain local lock behavior and distinguish delayed remote signals from failure.

## Content voice

- Tone: concise, calm, and specific.
- Terminology: use consistent Korean terms for lock, routine, emergency unlock, and selected apps.
- Microcopy rules: prefer action verbs, avoid vague “next” labels when the actual action can be named.

## Implementation constraints

- Prefer KDS components and tokens before feature-local styling.
- Feature-local components are acceptable when their product behavior is unique, but their
  visual primitives must still be KDS components.
- Do not import Material visual components listed in the ownership table from app screen code.
- Do not expose Material `Colors`, `Defaults`, or shape objects through new KDS public APIs.
- Extract a component when a pattern appears in multiple places, owns a visual state contract,
  or centralizes accessibility behavior.
- Avoid nested cards and decorative wrappers.
- Keep UI state in ViewModels or local state according to existing Orbit MVI patterns.
- Do not change navigation routes or side-effect semantics during visual cleanup.
- Framework: Jetpack Compose with Material 3 encapsulated behind the `:core:kds` module.
- Dependencies: do not add a design-system dependency solely to reproduce SEED visuals.
- Tests: color contracts belong in KDS unit tests; interaction semantics and screenshots belong in Compose tests.

## Open questions

- [ ] Define semantic spacing, radius, elevation, and motion token APIs after color migration stabilizes.
- [ ] Add KDS component previews for light/dark, font scaling, and enabled/pressed/loading/disabled states.
- [ ] Decide whether compact buttons should keep screen-owned spacing rather than `bottomSpacing`.

## Documentation Rules

- Update this file when tokens, reusable components, or screen-level conventions change.
- Keep `core/kds/README.md` and `core/kds/docs` aligned with this document and the actual KDS source.
- Before changing a KDS component, read its guide under `core/kds/docs/components` and the
  linked SEED source. A new `Keep*.kt` file requires a corresponding component guide.
- Record intentional SEED differences under `StopIt adaptation`; an undocumented difference
  is a defect, not a product customization.
- When a UI phase intentionally improves accessibility or consistency, record why the visible change is acceptable.
