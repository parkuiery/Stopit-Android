from pathlib import Path
import re
import unittest

from scripts import android_runtime_suites


REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID_CI = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
PLAY_DEPLOYMENT = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RUNTIME_QA = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
HOME_ACCESSIBILITY_TEST = REPO_ROOT / "app/src/androidTest/java/com/uiery/keep/qa/HomeAccessibilityPermissionIntegrationTest.kt"

HOME_ACCESSIBILITY_CLASS = "com.uiery.keep.qa.HomeAccessibilityPermissionIntegrationTest"


class HomeAccessibilityRuntimeSmokeContractTest(unittest.TestCase):
    def test_android_ci_runtime_smoke_manifest_includes_home_accessibility_regression(self):
        self.assertIn(
            HOME_ACCESSIBILITY_CLASS,
            android_runtime_suites.SUITES["android_ci_focused_runtime_smoke"],
        )
        self.assertIn("android_ci_focused_runtime_smoke", ANDROID_CI.read_text())

    def test_release_qa_remaining_runtime_manifest_includes_home_accessibility_regression(self):
        self.assertIn(
            HOME_ACCESSIBILITY_CLASS,
            android_runtime_suites.SUITES["release_remaining_runtime"],
        )
        self.assertIn("release_remaining_runtime", RELEASE_QA.read_text())

    def test_operator_docs_list_home_accessibility_runtime_smoke_or_manifest_boundary(self):
        for doc_path in (
            PLAY_DEPLOYMENT,
            RELEASE_CHECKLIST,
            RUNTIME_QA,
            RELEASE_CONTEXT,
        ):
            with self.subTest(path=doc_path.name):
                text = doc_path.read_text()
                self.assertIn("scripts/android_runtime_suites.py", text)
                self.assertTrue(
                    HOME_ACCESSIBILITY_CLASS in text or "release_remaining_runtime" in text,
                    f"{doc_path} should either name the Home accessibility test or the manifest suite that owns it",
                )

    def test_settings_round_trip_smoke_uses_device_relaunch_not_activityscenario_state_for_resume(self):
        source = HOME_ACCESSIBILITY_TEST.read_text()

        for method_name in (
            "returningFromAccessibilitySettingsResyncsHomePermissionDialogOnResume",
            "returningFromAccessibilitySettingsClearsHomePermissionDialogAfterReEnablingService",
        ):
            with self.subTest(method=method_name):
                method_source = self._method_source(source, method_name)
                self.assertNotIn(
                    "moveToState(Lifecycle.State",
                    method_source,
                    "Settings round-trip smoke should not force ActivityScenario state after "
                    "device relaunch. Remote CI can leave the original scenario STOPPED while "
                    "the device relaunch already brought StopIt foreground; assert via "
                    "foreground/dialog/secure-setting state instead.",
                )

    def _method_source(self, source: str, method_name: str) -> str:
        pattern = rf"fun {re.escape(method_name)}\(\) \{{(?P<body>.*?)(?=\n    @Test|\n    private suspend fun)"
        match = re.search(pattern, source, flags=re.DOTALL)
        if match is None:
            self.fail(f"Could not find method {method_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
