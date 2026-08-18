"""Static guard: routine_creation_cta_shown may only be emitted from a render report.

Background (#1166). PR #500 wired `HomeStatusCtaCard` into `HomeScreen` and logged the
shown event whenever `showRoutineCreationCta` flipped false -> true inside the ViewModel.
PR #1099 restored the v1.7.7 home UI and deleted the card's call site while keeping the
composable "as a test contract". The state-driven logging survived, so GA4 and Amplitude
recorded impressions of a card that was no longer on screen -- and `clicked` / `dismissed`
were structurally zero for a month. No Compose test caught it, because
`HomeStatusCtaCardIntegrationTest` rendered the composable directly.

The dead card was removed in the #463 cleanup, so today nothing emits the event at all and
zero is the correct number. #455 will reintroduce the nudge as a `HomeCard` variant chosen
by `HomeCardArbiter`. This guard is written to stay meaningful across both states: it does
not require the emission to exist, but if one exists it must sit in a render-reported entry
point rather than in a state-computation path.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN_SRC = REPO_ROOT / "app/src/main/java"

# The emission is a call through an injected KeepAnalytics, not the interface/adapter
# declaration in analytics/ -- matching the bare name would flag the definition site.
EMIT_CALL = "analytics.trackRoutineCreationCtaShown("
# A render-reported entry point is named for the moment the UI reports itself visible.
RENDER_ENTRY_POINT = re.compile(r"fun on[A-Za-z]*Shown\(")
# The state-transition logger that caused #1166. It must never come back by name.
BANNED_STATE_LOGGER = "trackRoutineCreationCtaShownIfNeeded"


def kotlin_sources() -> list[Path]:
    return sorted(MAIN_SRC.rglob("*.kt"))


def files_emitting_shown() -> list[Path]:
    return [p for p in kotlin_sources() if EMIT_CALL in p.read_text(encoding="utf-8")]


class RoutineCreationCtaShownContractTest(unittest.TestCase):
    def test_state_transition_logger_is_never_reintroduced(self) -> None:
        """The exact helper that logged on state change must stay gone."""
        offenders = [
            str(p.relative_to(REPO_ROOT))
            for p in kotlin_sources()
            if BANNED_STATE_LOGGER in p.read_text(encoding="utf-8")
        ]
        self.assertEqual(
            offenders,
            [],
            f"{BANNED_STATE_LOGGER} logged the impression when state flipped, which records "
            "a card that may never render (#1166). Report from the render instead.",
        )

    def test_every_emission_sits_in_a_render_reported_entry_point(self) -> None:
        """Zero emissions is a valid state; any emission must be render-reported.

        Today the dead Home CTA is removed and this passes with no emission at all. When
        #455 reintroduces the nudge, the new call site has to be reached from the UI
        reporting itself visible -- not from a path that merely computes visibility.
        """
        for path in files_emitting_shown():
            text = path.read_text(encoding="utf-8")
            rel = path.relative_to(REPO_ROOT)

            entry_points = [m.start() for m in RENDER_ENTRY_POINT.finditer(text)]
            self.assertTrue(
                entry_points,
                f"{rel} emits routine_creation_cta_shown but declares no render-reported "
                "entry point (a function named on...Shown()).",
            )

            for emit_index in (
                m.start() for m in re.finditer(re.escape(EMIT_CALL), text)
            ):
                nearest = [i for i in entry_points if i < emit_index]
                self.assertTrue(
                    nearest,
                    f"{rel} emits routine_creation_cta_shown before any render-reported "
                    "entry point, so it runs from a state-computation path (#1166).",
                )
                # Nothing else may open between the entry point and its emission, which
                # would mean the call actually belongs to a later function.
                between = text[max(nearest) : emit_index]
                self.assertNotIn(
                    "\n        private fun ",
                    between,
                    f"{rel}: the emission drifted out of the render-reported entry point.",
                )


if __name__ == "__main__":
    unittest.main()
