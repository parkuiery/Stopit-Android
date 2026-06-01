import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOWS = {
    "Android Release QA": REPO_ROOT / ".github" / "workflows" / "release-qa.yml",
    "Android Release Build": REPO_ROOT / ".github" / "workflows" / "release-build.yml",
    "Version Guard": REPO_ROOT / ".github" / "workflows" / "version-guard.yml",
}
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ReleaseGateRetargetTriggerTest(unittest.TestCase):
    def test_main_release_governance_workflows_run_when_pull_request_base_is_edited(self):
        for workflow_name, path in WORKFLOWS.items():
            with self.subTest(workflow=workflow_name):
                workflow = path.read_text()
                pull_request_block = self._pull_request_block(workflow)

                self.assertIn(
                    "branches: [main]",
                    pull_request_block,
                    f"{workflow_name} must remain scoped to main-target PRs",
                )
                self.assertRegex(
                    pull_request_block,
                    r"types:\s*\[[^\]]*\bedited\b[^\]]*\]",
                    f"{workflow_name} must run on pull_request edited so develop->main retarget materializes the gate",
                )

    def test_operator_docs_explain_retargeted_main_pr_gate_materialization(self):
        expected = "develop → main retarget"
        for path in (RELEASE_CHECKLIST, RELEASE_CONTEXT):
            with self.subTest(path=path.relative_to(REPO_ROOT)):
                doc = path.read_text()
                self.assertIn(expected, doc)
                self.assertIn("edited", doc)
                self.assertIn("Version Guard", doc)
                self.assertIn("Android Release QA", doc)
                self.assertIn("Android Release Build", doc)

    def _pull_request_block(self, workflow: str) -> str:
        match = re.search(r"(?ms)^  pull_request:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|^permissions:|^concurrency:|^env:|^jobs:|\Z)", workflow)
        self.assertIsNotNone(match, "workflow should declare an on.pull_request block")
        if match is None:
            self.fail("workflow should declare an on.pull_request block")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
