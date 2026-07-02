import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (REPO_ROOT / path).read_text(encoding="utf-8")


class RoutineCardVisualQaContractTest(unittest.TestCase):
    def test_runtime_qa_checklist_names_rendered_routine_card_evidence(self):
        checklist = read("docs/QA_RUNTIME_CHECKLIST.md")

        self.assertIn("RoutineListContentIntegrationTest", checklist)
        self.assertIn("routineCardsRenderStatusRepeatAndNextRunLabels", checklist)
        self.assertIn("status badge / repeat days / next run", checklist)

    def test_android_test_source_contains_enabled_disabled_and_running_coverage(self):
        source = read(
            "app/src/androidTest/java/com/uiery/keep/feature/routine/component/"
            "RoutineListContentIntegrationTest.kt"
        )

        for expected in [
            "routineCardsRenderStatusRepeatAndNextRunLabels",
            "routine_enabled_tag",
            "routine_disabled_tag",
            "routine_running_tag",
            "routine_next_run_label",
        ]:
            self.assertIn(expected, source)


if __name__ == "__main__":
    unittest.main()
