import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
OPS_CI = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"
GIT_WORKFLOW = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ActionlintGateContractTest(unittest.TestCase):
    def test_ops_ci_runs_for_any_workflow_change(self):
        workflow = OPS_CI.read_text()

        for trigger in ("pull_request", "push"):
            with self.subTest(trigger=trigger):
                block = self._trigger_block(workflow, trigger)
                self.assertIn("'.github/workflows/**'", block)
                self.assertNotIn("'.github/workflows/ops-ci.yml'", block)

    def test_ops_ci_has_dedicated_actionlint_job(self):
        workflow = OPS_CI.read_text()

        self.assertRegex(
            workflow,
            r"(?ms)^  actionlint:\n.*?name: Workflow syntax lint",
            "Ops CI should expose a dedicated Workflow syntax lint job",
        )
        self.assertIn("rhysd/actionlint", workflow)
        self.assertRegex(workflow, r"(?m)^\s+run:\s+actionlint\b")

    def test_operator_docs_describe_remote_actionlint_gate(self):
        git_workflow = GIT_WORKFLOW.read_text()
        release_context = RELEASE_CONTEXT.read_text()

        for doc_name, doc in (
            ("docs/GIT_WORKFLOW.md", git_workflow),
            ("docs/ops/stopit/release-context.md", release_context),
        ):
            with self.subTest(doc=doc_name):
                self.assertIn("Workflow syntax lint", doc)
                self.assertIn("actionlint", doc)
                self.assertIn(".github/workflows/**", doc)

    def _trigger_block(self, workflow: str, trigger: str) -> str:
        pattern = rf"(?ms)^  {trigger}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|^permissions:|^concurrency:|^jobs:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare on.{trigger}")
        if match is None:
            self.fail(f"workflow should declare on.{trigger}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
