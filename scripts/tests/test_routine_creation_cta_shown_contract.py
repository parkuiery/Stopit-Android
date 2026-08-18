"""Static guard: routine_creation_cta_shown must be reported from render, not from state.

Background (#1166). PR #500 wired `HomeStatusCtaCard` into `HomeScreen`, and the shown
event was logged whenever `showRoutineCreationCta` flipped false -> true inside the
ViewModel. PR #1099 then restored the v1.7.7 home UI and deleted the card's call site
while keeping the composable "as a test contract". The state-driven logging survived, so
GA4 and Amplitude kept recording impressions of a card that was no longer on screen --
and `clicked` / `dismissed` were structurally zero for a month.

No Compose test caught it: `HomeStatusCtaCardIntegrationTest` renders the composable
directly, so it passes whether or not anything renders it in the real screen. That is
exactly why this guard is static text analysis rather than another Compose test.

The rule enforced here is narrow and durable: the analytics call belongs to a
render-reported entry point, and the state-computation paths may only reset the
once-per-appearance flag.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
HOME_VIEW_MODEL = REPO_ROOT / "app/src/main/java/com/uiery/keep/feature/home/HomeViewModel.kt"
HOME_SCREEN = REPO_ROOT / "app/src/main/java/com/uiery/keep/feature/home/HomeScreen.kt"

RENDER_ENTRY_POINT = "onRoutineCreationCtaShown"
RESET_HELPER = "resetRoutineCreationCtaShownLog"
ANALYTICS_CALL = "analytics.trackRoutineCreationCtaShown("


class RoutineCreationCtaShownContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.view_model = HOME_VIEW_MODEL.read_text(encoding="utf-8")
        self.screen = HOME_SCREEN.read_text(encoding="utf-8")

    def test_analytics_call_exists_exactly_once(self) -> None:
        """More than one call site means a second path can log an impression."""
        self.assertEqual(
            self.view_model.count(ANALYTICS_CALL),
            1,
            "trackRoutineCreationCtaShown must have exactly one call site in HomeViewModel.",
        )

    def test_analytics_call_lives_in_the_render_reported_entry_point(self) -> None:
        """The only logger must be the function the UI calls when the CTA appears."""
        entry_index = self.view_model.find(f"fun {RENDER_ENTRY_POINT}()")
        self.assertNotEqual(
            entry_index, -1, f"HomeViewModel must expose {RENDER_ENTRY_POINT}()."
        )

        call_index = self.view_model.find(ANALYTICS_CALL)
        self.assertGreater(
            call_index,
            entry_index,
            f"trackRoutineCreationCtaShown must be logged from {RENDER_ENTRY_POINT}(), "
            "not from an earlier state-computation path.",
        )

        # Nothing else may sit between the entry point and its analytics call, which
        # would mean the call actually belongs to a later function.
        between = self.view_model[entry_index:call_index]
        self.assertNotIn(
            "\n        private fun ",
            between,
            "trackRoutineCreationCtaShown drifted out of the render-reported entry point.",
        )

    def test_state_paths_only_reset_the_flag(self) -> None:
        """State recomputation may clear the once-per-appearance flag, never log."""
        self.assertGreaterEqual(
            self.view_model.count(f"{RESET_HELPER}("),
            2,
            f"State-computation paths must call {RESET_HELPER}(...) to re-arm the flag.",
        )
        self.assertNotIn(
            "trackRoutineCreationCtaShownIfNeeded",
            self.view_model,
            "The state-transition logger was reintroduced; impressions would be recorded "
            "for a card that may never render (#1166).",
        )

    def test_screen_reports_shown_from_the_rendered_branch(self) -> None:
        """The report must be inside the branch that renders the CTA, not beside it."""
        self.assertIn(
            "onRoutineCreationShown",
            self.screen,
            "HomeStatusCtaCard must accept a shown-reporting callback.",
        )

        branch = re.search(
            r"if \(model\.showRoutineCreationSecondary\) \{(.*?)\n {16}\}",
            self.screen,
            re.DOTALL,
        )
        self.assertIsNotNone(
            branch, "Could not locate the showRoutineCreationSecondary render branch."
        )
        body = branch.group(1)
        self.assertIn(
            "onRoutineCreationShown()",
            body,
            "The shown report must fire from inside the rendered branch so that a screen "
            "which never composes the CTA also never records an impression.",
        )
        self.assertIn(
            "LaunchedEffect",
            body,
            "The shown report must be a composition side effect, not a call on every "
            "recomposition.",
        )


if __name__ == "__main__":
    unittest.main()
