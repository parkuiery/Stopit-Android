import pathlib
import unittest
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
QA_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
DEFAULT_STRINGS = REPO_ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
LOCALIZED_STRING_DIRS = sorted((REPO_ROOT / "app" / "src" / "main" / "res").glob("values-*"))
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


def _strings(path: pathlib.Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: (element.text or "")
        for element in root.findall("string")
    }


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

    def test_active_routine_blocked_message_is_localized_in_shipped_locales(self):
        key = "routine_active_action_blocked_message"
        default_text = _strings(DEFAULT_STRINGS)[key]
        offenders = []

        for values_dir in LOCALIZED_STRING_DIRS:
            strings_path = values_dir / "strings.xml"
            if not strings_path.exists():
                continue
            localized_text = _strings(strings_path).get(key)
            if localized_text == default_text:
                offenders.append(values_dir.name)

        self.assertEqual(
            [],
            offenders,
            "Active routine block guidance is high-trust #609 copy and must not ship as copied default English in localized resources.",
        )


if __name__ == "__main__":
    unittest.main()
