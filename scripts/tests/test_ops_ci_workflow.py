import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
OPS_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class OpsCiWorkflowTest(unittest.TestCase):
    def test_ops_ci_workflow_verifies_functions_and_release_helpers(self):
        self.assertTrue(OPS_CI_WORKFLOW.exists(), "ops-ci workflow should exist")
        workflow = OPS_CI_WORKFLOW.read_text()

        self.assertIn("name: Ops CI", workflow)
        self.assertIn("functions/**", workflow)
        self.assertIn("scripts/notify-discord-deploy.py", workflow)
        self.assertIn("scripts/promote-google-play-track.js", workflow)
        self.assertIn("scripts/check-release-readiness.sh", workflow)
        self.assertIn("scripts/check-latest-production-deployed.sh", workflow)
        self.assertIn("scripts/release-start.sh", workflow)
        self.assertIn("scripts/bump-version.sh", workflow)
        self.assertIn("scripts/validate-play-deploy-ref.sh", workflow)
        self.assertIn("scripts/play_version_code_guard.py", workflow)
        self.assertIn("scripts/tests/**", workflow)
        self.assertIn("actions/setup-node", workflow)
        self.assertIn("node-version: '22'", workflow)
        self.assertIn("npm ci", workflow)
        self.assertIn("npm run lint", workflow)
        self.assertIn("npm test", workflow)
        self.assertIn("node --test scripts/tests/test_promote_google_play_track.js", workflow)
        self.assertIn("python3 -m py_compile scripts/notify-discord-deploy.py", workflow)
        self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", workflow)
        self.assertIn("bash -n scripts/check-release-readiness.sh scripts/check-latest-production-deployed.sh scripts/release-start.sh scripts/bump-version.sh scripts/validate-play-deploy-ref.sh", workflow)

    def test_docs_only_changes_materialize_docs_contract_gate_without_functions_or_android_builds(self):
        workflow = OPS_CI_WORKFLOW.read_text()

        for trigger in ("pull_request", "push"):
            with self.subTest(trigger=trigger):
                block = self._trigger_block(workflow, trigger)
                self.assertIn("'docs/**'", block)
                self.assertIn("'**/*.md'", block)

        self.assertIn("docs_contract:", workflow)
        self.assertIn("release_helpers:", workflow)
        self.assertRegex(
            workflow,
            r"(?ms)^  docs-contract:\n.*?name: Docs/runbook contract tests",
            "Ops CI should expose a lightweight docs-only contract job",
        )
        self.assertIn("Run docs/runbook contract tests", workflow)
        self.assertIn("scripts.tests.test_play_deploy_secret_contract_runbook", workflow)
        self.assertIn("scripts.tests.test_release_build_workflow_scope", workflow)
        self.assertIn("scripts.tests.test_release_qa_runtime_gate_docs", workflow)
        self.assertIn("scripts.tests.test_android_ci_runtime_smoke_docs", workflow)
        self.assertIn("scripts.tests.test_release_guard_hotfix_sync", workflow)
        self.assertIn("scripts.tests.test_ops_ci_workflow", workflow)
        self.assertIn("scripts.tests.test_actionlint_gate", workflow)
        docs_contract_job = self._job_block(workflow, "docs-contract")
        self.assertNotRegex(
            docs_contract_job,
            r"(?ms)(npm ci|setup-java|gradlew|connectedDevDebugAndroidTest)",
            "Docs-only contract gate must stay lightweight and avoid Functions/Android work",
        )
        self.assertRegex(
            workflow,
            r"(?ms)^  functions:\n.*?if:.*ops_ci\.outputs\.functions",
            "Firebase Functions verification should not run for docs-only trigger materialization",
        )

    def test_operator_docs_name_ops_ci_responsibility(self):
        docs = [GIT_WORKFLOW_DOC.read_text(), RELEASE_CONTEXT_DOC.read_text()]
        for doc in docs:
            self.assertIn("Ops CI", doc)
            self.assertIn("functions/", doc)
            self.assertIn("scripts/promote-google-play-track.js", doc)
            self.assertIn("scripts/notify-discord-deploy.py", doc)
            self.assertIn("release-helper guardrail", doc)
            self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", doc)
            self.assertIn("Docs/runbook contract tests", doc)
            self.assertIn("docs-only", doc)

    def _trigger_block(self, workflow: str, trigger: str) -> str:
        pattern = rf"(?ms)^  {trigger}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|^permissions:|^concurrency:|^jobs:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare on.{trigger}")
        if match is None:
            self.fail(f"workflow should declare on.{trigger}")
        return match.group("body")

    def _job_block(self, workflow: str, job_id: str) -> str:
        pattern = rf"(?ms)^  {re.escape(job_id)}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare jobs.{job_id}")
        if match is None:
            self.fail(f"workflow should declare jobs.{job_id}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
