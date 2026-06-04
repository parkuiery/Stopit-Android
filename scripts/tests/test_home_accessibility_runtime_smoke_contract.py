from pathlib import Path
import unittest


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
    def test_android_ci_runtime_smoke_includes_home_accessibility_regression(self):
        self.assertIn(HOME_ACCESSIBILITY_CLASS, ANDROID_CI.read_text())

    def test_release_qa_remaining_runtime_gate_includes_home_accessibility_regression(self):
        self.assertIn(HOME_ACCESSIBILITY_CLASS, RELEASE_QA.read_text())

    def test_operator_docs_list_home_accessibility_runtime_smoke(self):
        for doc_path in (
            PLAY_DEPLOYMENT,
            RELEASE_CHECKLIST,
            RUNTIME_QA,
            RELEASE_CONTEXT,
        ):
            with self.subTest(path=doc_path.name):
                self.assertIn(HOME_ACCESSIBILITY_CLASS, doc_path.read_text())

    def test_home_accessibility_smoke_uses_device_relaunch_not_activityscenario_state_for_resume(self):
        source = HOME_ACCESSIBILITY_TEST.read_text()

        self.assertNotIn(
            "moveToState(Lifecycle.State",
            source,
            "Home accessibility runtime smoke should not force ActivityScenario state after "
            "Settings/device relaunch. Remote CI can leave the original scenario STOPPED while "
            "the device relaunch already brought StopIt foreground; assert via foreground/dialog "
            "state instead.",
        )
        self.assertNotIn(
            "import androidx.lifecycle.Lifecycle",
            source,
            "Lifecycle import should stay absent when the smoke relies on device foreground state.",
        )


if __name__ == "__main__":
    unittest.main()
