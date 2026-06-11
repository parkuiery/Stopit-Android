import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
ANDROID_SKILLS_TESTING_QA = REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"


class RuntimeDiagnosticArtifactsTest(unittest.TestCase):
    def test_android_ci_runtime_smoke_uploads_diagnostics_even_after_failure(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        run_step = self._step_block(workflow, "Run focused Android runtime smoke gate")
        upload_step = self._step_block(workflow, "Upload runtime smoke diagnostics")

        self.assertIn("mkdir -p runtime-diagnostics", run_step)
        self.assertIn("adb logcat -d", run_step)
        self.assertIn("runtime-diagnostics/android-ci-logcat.txt", run_step)
        self.assertIn("adb shell dumpsys alarm", run_step)
        self.assertIn("runtime-diagnostics/android-ci-alarm.txt", run_step)
        self.assertIn("adb shell dumpsys accessibility", run_step)
        self.assertIn("runtime-diagnostics/android-ci-accessibility.txt", run_step)

        self.assertIn("if: always() && steps.runtime-firebase-config.outputs.available == 'true'", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-runtime-smoke-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("app/build/reports/androidTests/**", upload_step)
        self.assertIn("app/build/outputs/androidTest-results/**", upload_step)
        self.assertIn("runtime-diagnostics/**", upload_step)

    def test_release_instrumentation_qa_uploads_diagnostics_even_after_failure(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()
        run_step = self._step_block(workflow, "Run Android testing skill UI smoke and runtime QA")
        upload_step = self._step_block(workflow, "Upload release instrumentation diagnostics")

        self.assertIn("mkdir -p runtime-diagnostics", run_step)
        self.assertIn("adb logcat -d", run_step)
        self.assertIn("runtime-diagnostics/release-qa-logcat.txt", run_step)
        self.assertIn("adb shell dumpsys alarm", run_step)
        self.assertIn("runtime-diagnostics/release-qa-alarm.txt", run_step)
        self.assertIn("adb shell dumpsys accessibility", run_step)
        self.assertIn("runtime-diagnostics/release-qa-accessibility.txt", run_step)

        self.assertIn("if: always()", upload_step)
        self.assertIn("continue-on-error: true", upload_step)
        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-release-instrumentation-diagnostics", upload_step)
        self.assertIn("retention-days: 7", upload_step)
        self.assertIn("app/build/reports/androidTests/**", upload_step)
        self.assertIn("app/build/outputs/androidTest-results/**", upload_step)
        self.assertIn("runtime-diagnostics/**", upload_step)

    def test_operator_docs_explain_runtime_diagnostic_artifact_triage_order(self):
        required = [
            "stopit-runtime-smoke-diagnostics",
            "stopit-release-instrumentation-diagnostics",
            "app/build/reports/androidTests",
            "app/build/outputs/androidTest-results",
            "logcat",
            "dumpsys alarm",
            "dumpsys accessibility",
            "retention-days: 7",
            "non-blocking",
        ]
        for path in [QA_RUNTIME_CHECKLIST, ANDROID_SKILLS_TESTING_QA, RELEASE_CHECKLIST]:
            text = path.read_text()
            for phrase in required:
                with self.subTest(doc=path.name, phrase=phrase):
                    self.assertIn(phrase, text)

    def _step_block(self, workflow: str, step_name: str) -> str:
        pattern = rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare step {step_name}")
        if match is None:
            self.fail(f"workflow should declare step {step_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
