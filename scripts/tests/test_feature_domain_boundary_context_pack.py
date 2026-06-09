import pathlib
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ENGINEERING_CONTEXT = REPO_ROOT / "docs/ops/stopit/engineering-context.md"
FEATURE_DOMAIN_RUNBOOK = REPO_ROOT / "docs/FEATURE_DOMAIN_BOUNDARY.md"


class FeatureDomainBoundaryContextPackTest(unittest.TestCase):
    def test_engineering_context_names_completed_shared_boundaries(self):
        context = ENGINEERING_CONTEXT.read_text()

        for required_phrase in (
            "RepeatBlockRoutineSuggestionAnalyticsPayload",
            "LockHistorySessionWriter",
            "feature-local suggestion object",
            "feature-private 모델/저장소로 되돌리지 않는다",
        ):
            self.assertIn(required_phrase, context)

    def test_engineering_context_keeps_remaining_migration_axes_and_source_of_truth(self):
        context = ENGINEERING_CONTEXT.read_text()
        runbook = FEATURE_DOMAIN_RUNBOOK.read_text()

        self.assertIn("FEATURE_DOMAIN_BOUNDARY.md", context)
        self.assertIn("current inventory", context)

        for required_axis in (
            "GoalLock shared domain",
            "Routine runtime repository/use-case",
            "ParentMode runtime session/policy/store",
        ):
            self.assertIn(required_axis, context)

        self.assertIn("현재 production drift inventory", runbook)
        self.assertIn("RepeatBlock analytics DTO boundary", runbook)
        self.assertIn("LockHistory runtime recording boundary", runbook)

    def test_docs_lane_does_not_duplicate_active_code_pr_source_of_truth_edits(self):
        context = ENGINEERING_CONTEXT.read_text()

        self.assertIn("open code PR", context)
        self.assertIn("중복 docs PR을 만들지 않는다", context)


if __name__ == "__main__":
    unittest.main()
