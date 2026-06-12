import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
QA_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ACCESSIBILITY_RUNTIME_TEST = (
    REPO_ROOT
    / "app"
    / "src"
    / "androidTest"
    / "java"
    / "com"
    / "uiery"
    / "keep"
    / "service"
    / "KeepAccessibilityServiceIntegrationTest.kt"
)


class ActiveRoutineEnforcementContractTest(unittest.TestCase):
    def test_qa_checklist_pins_active_routine_foreground_runtime_evidence(self):
        checklist = QA_CHECKLIST.read_text()

        self.assertIn("Issue: #609", checklist)
        self.assertIn(
            "KeepAccessibilityServiceIntegrationTest#activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution",
            checklist,
        )
        self.assertIn("block_source=routine", checklist)
        self.assertIn("routine_id", checklist)
        self.assertIn("foreground", checklist)
        self.assertIn("release/tag/Play deploy", checklist)

    def test_accessibility_runtime_test_asserts_active_routine_attribution(self):
        source = ACCESSIBILITY_RUNTIME_TEST.read_text()

        self.assertIn(
            "fun activeRoutineWithoutManualKeep_launchesBlockActivityWithRoutineAttribution()",
            source,
        )
        self.assertIn("AnalyticsBlockSource.ROUTINE", source)
        self.assertIn("lastLaunchedRoutineId", source)
        self.assertIn("ROUTINE_RUNTIME_TEST_ID", source)


if __name__ == "__main__":
    unittest.main()
