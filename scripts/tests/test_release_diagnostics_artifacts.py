import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
ANDROID_SKILLS_TESTING_QA = REPO_ROOT / "docs" / "ANDROID_SKILLS_TESTING_QA.md"
QA_RUNTIME_CHECKLIST = REPO_ROOT / "docs" / "QA_RUNTIME_CHECKLIST.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ReleaseDiagnosticsArtifactsTest(unittest.TestCase):
    def _upload_step(self, workflow_name: str, step_name: str) -> str:
        workflow = (WORKFLOW_DIR / workflow_name).read_text()
        match = re.search(
            rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name: |\Z)",
            workflow,
        )
        self.assertIsNotNone(match, f"{workflow_name} should contain upload step {step_name!r}")
        if match is None:
            self.fail(f"{workflow_name} should contain upload step {step_name!r}")
        return match.group("body")

    def test_release_build_diagnostics_uploads_gradle_problems_report(self):
        step = self._upload_step("release-build.yml", "Upload release build diagnostics")
        self.assertIn("app/build/reports/problems/**", step)
        self.assertIn("if-no-files-found: ignore", step)
        self.assertIn("continue-on-error: true", step)
        self.assertIn("retention-days: 7", step)

    def test_release_qa_build_diagnostics_uploads_gradle_problems_report(self):
        step = self._upload_step("release-qa.yml", "Upload full release QA diagnostics")
        self.assertIn("app/build/reports/problems/**", step)
        self.assertIn("if-no-files-found: ignore", step)
        self.assertIn("continue-on-error: true", step)
        self.assertIn("retention-days: 7", step)

    def test_release_instrumentation_diagnostics_uploads_gradle_problems_report(self):
        step = self._upload_step("release-qa.yml", "Upload release instrumentation diagnostics")
        self.assertIn("app/build/reports/problems/**", step)
        self.assertIn("if-no-files-found: ignore", step)
        self.assertIn("continue-on-error: true", step)
        self.assertIn("retention-days: 7", step)

    def test_play_deploy_release_diagnostics_uploads_gradle_problems_report(self):
        step = self._upload_step("play-deploy.yml", "Upload Play deploy release diagnostics")
        self.assertIn("app/build/reports/problems/**", step)
        self.assertIn("if-no-files-found: ignore", step)
        self.assertIn("continue-on-error: true", step)
        self.assertIn("retention-days: 7", step)

    def test_operator_docs_name_gradle_problems_report_in_release_diagnostics_triage(self):
        docs = {
            "Android skills QA": ANDROID_SKILLS_TESTING_QA.read_text(),
            "QA runtime checklist": QA_RUNTIME_CHECKLIST.read_text(),
            "Release checklist": RELEASE_CHECKLIST.read_text(),
            "Stopit release context": RELEASE_CONTEXT.read_text(),
        }
        for name, content in docs.items():
            with self.subTest(doc=name):
                self.assertIn("app/build/reports/problems", content)
                self.assertIn("Gradle problems", content)
                self.assertIn("stopit-release-build-diagnostics", content)
                self.assertIn("stopit-release-qa-build-diagnostics", content)
                self.assertIn("stopit-play-deploy-release-diagnostics", content)


if __name__ == "__main__":
    unittest.main()
