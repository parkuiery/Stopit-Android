import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
OPS_CI = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"
GIT_WORKFLOW = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
GOVERNANCE_RELEASE_WORKFLOWS = (
    "android-ci.yml",
    "branch-hygiene.yml",
    "ops-ci.yml",
    "play-deploy.yml",
    "release-build.yml",
    "release-qa.yml",
    "version-guard.yml",
)


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

    def test_ops_ci_pins_actionlint_version_instead_of_floating_latest(self):
        workflow = OPS_CI.read_text()

        self.assertIn("ACTIONLINT_VERSION", workflow)
        self.assertRegex(workflow, r"ACTIONLINT_VERSION:\s+\d+\.\d+\.\d+")
        self.assertNotIn("bash -s -- latest", workflow)
        self.assertRegex(workflow, r"bash -s -- \"\$ACTIONLINT_VERSION\" /usr/local/bin")

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
                self.assertIn("1.7.12", doc)
                self.assertIn(".github/workflows/**", doc)

    def test_governance_release_workflows_use_checkout_v6(self):
        for workflow_name in GOVERNANCE_RELEASE_WORKFLOWS:
            workflow = (WORKFLOW_DIR / workflow_name).read_text()
            checkout_refs = re.findall(r"actions/checkout@v(\d+)", workflow)
            with self.subTest(workflow=workflow_name):
                self.assertTrue(checkout_refs, f"{workflow_name} should use actions/checkout")
                self.assertEqual(
                    {"6"},
                    set(checkout_refs),
                    f"{workflow_name} should stay on the repository checkout major standard v6",
                )

    def test_operator_docs_describe_checkout_major_standard_for_all_release_governance_workflows(self):
        git_workflow = GIT_WORKFLOW.read_text()
        release_context = RELEASE_CONTEXT.read_text()

        for doc_name, doc in (
            ("docs/GIT_WORKFLOW.md", git_workflow),
            ("docs/ops/stopit/release-context.md", release_context),
        ):
            with self.subTest(doc=doc_name):
                self.assertIn("actions/checkout", doc)
                self.assertIn("v6", doc)
                self.assertIn("Branch Hygiene", doc)
                self.assertIn("Release QA", doc)

    def _trigger_block(self, workflow: str, trigger: str) -> str:
        pattern = rf"(?ms)^  {trigger}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|^permissions:|^concurrency:|^jobs:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare on.{trigger}")
        if match is None:
            self.fail(f"workflow should declare on.{trigger}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
