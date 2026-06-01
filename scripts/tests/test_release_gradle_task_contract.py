import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_HELPERS = [
    REPO_ROOT / "scripts" / "check-release-readiness.sh",
    REPO_ROOT / "scripts" / "bump-version.sh",
]
RELEASE_WORKFLOWS = [
    REPO_ROOT / ".github" / "workflows" / "release-build.yml",
    REPO_ROOT / ".github" / "workflows" / "play-deploy.yml",
]
BRANCH_HYGIENE_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "branch-hygiene.yml"


class ReleaseGradleTaskContractTest(unittest.TestCase):
    def test_release_helper_dry_runs_use_app_qualified_tasks(self):
        expected = ":app:testProdReleaseUnitTest :app:bundleProdRelease --dry-run"
        for path in RELEASE_HELPERS:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn(expected, text)
                self.assertIsNone(
                    re.search(r"\.\/gradlew\s+testProdReleaseUnitTest\s+bundleProdRelease\s+--dry-run", text),
                    f"{path} should not rely on root task-name inference for app release tasks",
                )

    def test_release_workflows_use_app_qualified_release_tasks(self):
        for path in RELEASE_WORKFLOWS:
            with self.subTest(path=path.name):
                text = path.read_text()
                self.assertIn(":app:testProdReleaseUnitTest", text)
                self.assertIn(":app:bundleProdRelease", text)
                self.assertIsNone(
                    re.search(r"run:\s*\.\/gradlew\s+testProdReleaseUnitTest\b", text),
                    f"{path} should not rely on root task-name inference for release unit tests",
                )
                self.assertIsNone(
                    re.search(r"run:\s*\.\/gradlew\s+bundleProdRelease\b", text),
                    f"{path} should not rely on root task-name inference for release bundles",
                )

    def test_branch_hygiene_rejects_unqualified_release_workflow_tasks(self):
        text = BRANCH_HYGIENE_WORKFLOW.read_text()
        self.assertRegex(text, r"testProdReleaseUnitTest")
        self.assertRegex(text, r"bundleProdRelease")


if __name__ == "__main__":
    unittest.main()
