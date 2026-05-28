import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github/workflows/android-ci.yml"
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github/workflows/release-qa.yml"


class LintRegistryWorkflowTest(unittest.TestCase):
    def test_android_ci_verifies_dev_lint_registry_report(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()

        self.assertIn("Verify Navigation/Compose lint registry coverage", workflow)
        self.assertIn("--report app/build/reports/lint-results-devDebug.html", workflow)

    def test_release_qa_verifies_prod_release_lint_registry_report(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()

        self.assertIn("Verify Navigation/Compose lint registry coverage", workflow)
        self.assertIn("--report app/build/reports/lint-results-prodRelease.html", workflow)


if __name__ == "__main__":
    unittest.main()
