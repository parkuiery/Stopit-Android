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
        self.assertIn("scripts/validate-play-rollout-inputs.js", workflow)
        self.assertIn("scripts/play_version_code_guard.py", workflow)
        self.assertIn("scripts/release_provenance_manifest.py", workflow)
        self.assertIn("scripts/check_workflow_gradle_tasks.py", workflow)
        self.assertIn("scripts/release-tag.sh", workflow)
        self.assertIn("scripts/check-play-deploy-secret-contract.sh", workflow)
        self.assertIn("scripts/setup-play-deploy-secrets.sh", workflow)
        self.assertIn("scripts/setup-discord-deploy-secrets.sh", workflow)
        self.assertIn("scripts/tests/**", workflow)
        self.assertIn("actions/setup-node", workflow)
        self.assertIn("node-version: '22'", workflow)
        self.assertIn("npm ci", workflow)
        self.assertIn("npm run lint", workflow)
        self.assertIn("npm test", workflow)
        self.assertIn("node --test scripts/tests/test_promote_google_play_track.js", workflow)
        self.assertIn("node --check scripts/validate-play-rollout-inputs.js", workflow)
        self.assertIn("python3 -m py_compile scripts/notify-discord-deploy.py", workflow)
        self.assertIn("python3 -m py_compile scripts/release_provenance_manifest.py", workflow)
        self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", workflow)
        self.assertIn("bash -n scripts/check-release-readiness.sh scripts/check-latest-production-deployed.sh scripts/release-start.sh scripts/bump-version.sh scripts/validate-play-deploy-ref.sh scripts/release-tag.sh scripts/check-play-deploy-secret-contract.sh scripts/setup-play-deploy-secrets.sh scripts/setup-discord-deploy-secrets.sh", workflow)

    def test_release_helper_scope_includes_operator_helper_scripts(self):
        workflow = OPS_CI_WORKFLOW.read_text()
        helper_scripts = [
            "scripts/release-tag.sh",
            "scripts/check-play-deploy-secret-contract.sh",
            "scripts/setup-play-deploy-secrets.sh",
            "scripts/setup-discord-deploy-secrets.sh",
            "scripts/validate-play-rollout-inputs.js",
            "scripts/check_workflow_gradle_tasks.py",
            "scripts/release_provenance_manifest.py",
        ]
        shell_helper_scripts = [
            script for script in helper_scripts if script.endswith(".sh")
        ]

        for trigger in ("pull_request", "push"):
            block = self._trigger_block(workflow, trigger)
            with self.subTest(trigger=trigger):
                for script in helper_scripts:
                    self.assertIn(f"'{script}'", block)

        release_helpers_filter = self._filter_block(workflow, "release_helpers")
        for script in helper_scripts:
            with self.subTest(filter="release_helpers", script=script):
                self.assertIn(f"'{script}'", release_helpers_filter)

        syntax_step = self._step_block(workflow, "Check release-helper shell syntax")
        for script in shell_helper_scripts:
            with self.subTest(step="shell syntax", script=script):
                self.assertIn(script, syntax_step)

        rollout_syntax_step = self._step_block(workflow, "Check staged rollout validator syntax")
        self.assertIn("node --check scripts/validate-play-rollout-inputs.js", rollout_syntax_step)

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
        self.assertIn("scripts.tests.test_release_qa_workflow_scope", workflow)
        self.assertIn("scripts.tests.test_release_qa_runtime_gate_docs", workflow)
        self.assertIn("scripts.tests.test_android_ci_runtime_smoke_docs", workflow)
        self.assertIn("scripts.tests.test_release_guard_hotfix_sync", workflow)
        self.assertIn("scripts.tests.test_release_provenance_workflow_contract", workflow)
        self.assertIn("scripts.tests.test_acquisition_attribution_docs_contract", workflow)
        self.assertIn("scripts.tests.test_ga4_custom_dimension_registration_docs", workflow)
        self.assertIn("scripts.tests.test_monetization_interest_contract", workflow)
        self.assertIn("scripts.tests.test_signed_aab_lint_gate", workflow)
        self.assertIn("scripts.tests.test_ops_ci_workflow", workflow)
        self.assertIn("scripts.tests.test_actionlint_gate", workflow)
        self.assertIn("scripts.tests.test_workflow_gradle_task_guard", workflow)
        self.assertIn("scripts.tests.test_release_gradle_task_contract", workflow)
        docs_contract_filter = self._filter_block(workflow, "docs_contract")
        self.assertIn("'scripts/tests/test_acquisition_attribution_docs_contract.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_ga4_custom_dimension_registration_docs.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_monetization_interest_contract.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_review_prompt_post_release_followthrough_docs.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_signed_aab_lint_gate.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_workflow_gradle_task_guard.py'", docs_contract_filter)
        self.assertIn("'scripts/tests/test_release_gradle_task_contract.py'", docs_contract_filter)
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

    def test_release_workflow_changes_materialize_docs_contract_gate(self):
        workflow = OPS_CI_WORKFLOW.read_text()
        docs_contract_filter = self._filter_block(workflow, "docs_contract")
        docs_contract_job = self._job_block(workflow, "docs-contract")
        release_workflow_paths = [
            ".github/workflows/android-ci.yml",
            ".github/workflows/release-qa.yml",
            ".github/workflows/release-build.yml",
            ".github/workflows/play-deploy.yml",
            ".github/workflows/version-guard.yml",
        ]

        for workflow_path in release_workflow_paths:
            with self.subTest(filter="docs_contract", workflow_path=workflow_path):
                self.assertIn(f"'{workflow_path}'", docs_contract_filter)

        expected_contract_modules = [
            "scripts.tests.test_release_qa_runtime_gate_docs",
            "scripts.tests.test_release_qa_workflow_scope",
            "scripts.tests.test_android_ci_runtime_smoke_docs",
            "scripts.tests.test_release_build_workflow_scope",
            "scripts.tests.test_release_provenance_workflow_contract",
            "scripts.tests.test_acquisition_attribution_docs_contract",
            "scripts.tests.test_ga4_custom_dimension_registration_docs",
            "scripts.tests.test_monetization_interest_contract",
            "scripts.tests.test_signed_aab_lint_gate",
            "scripts.tests.test_review_prompt_post_release_followthrough_docs",
            "scripts.tests.test_play_deploy_secret_contract_runbook",
            "scripts.tests.test_release_guard_hotfix_sync",
            "scripts.tests.test_workflow_gradle_task_guard",
            "scripts.tests.test_release_gradle_task_contract",
        ]
        for module in expected_contract_modules:
            with self.subTest(job="docs-contract", module=module):
                self.assertIn(module, docs_contract_job)

    def test_operator_docs_name_workflow_contract_materialization_boundary(self):
        git_workflow = GIT_WORKFLOW_DOC.read_text()
        release_context = RELEASE_CONTEXT_DOC.read_text()
        combined_docs = git_workflow + "\n" + release_context

        self.assertIn("workflow 변경 PR", combined_docs)
        self.assertIn("actionlint-only green", combined_docs)
        self.assertIn("contract-test green", combined_docs)
        self.assertIn("release/CI/CD workflow", combined_docs)
        self.assertIn("Docs/runbook contract tests", combined_docs)

    def test_operator_docs_name_ops_ci_responsibility(self):
        git_workflow = GIT_WORKFLOW_DOC.read_text()
        release_context = RELEASE_CONTEXT_DOC.read_text()
        docs = [git_workflow, release_context]
        for doc in docs:
            self.assertIn("Ops CI", doc)
            self.assertIn("functions/", doc)
            self.assertIn("scripts/promote-google-play-track.js", doc)
            self.assertIn("scripts/notify-discord-deploy.py", doc)
            self.assertIn("release-helper guardrail", doc)
            self.assertIn("scripts/release_provenance_manifest.py", doc)
            self.assertIn("scripts/check_workflow_gradle_tasks.py", doc)
            self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", doc)
            self.assertIn("Docs/runbook contract tests", doc)
            self.assertIn("scripts.tests.test_acquisition_attribution_docs_contract", doc)
            self.assertIn("scripts.tests.test_ga4_custom_dimension_registration_docs", doc)
            self.assertIn("scripts.tests.test_monetization_interest_contract", doc)
            self.assertIn("scripts.tests.test_signed_aab_lint_gate", doc)
            self.assertIn("scripts.tests.test_review_prompt_post_release_followthrough_docs", doc)
            self.assertIn("scripts.tests.test_workflow_gradle_task_guard", doc)
            self.assertIn("scripts.tests.test_release_gradle_task_contract", doc)
            self.assertIn("docs-only", doc)

        # The main operator workflow table should enumerate the full release-helper
        # path surface so humans can predict which helper-only PRs materialize Ops CI.
        for script in [
            "scripts/release-tag.sh",
            "scripts/check-play-deploy-secret-contract.sh",
            "scripts/setup-play-deploy-secrets.sh",
            "scripts/setup-discord-deploy-secrets.sh",
            "scripts/validate-play-rollout-inputs.js",
        ]:
            with self.subTest(doc="GIT_WORKFLOW", script=script):
                self.assertIn(script, git_workflow)

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

    def _filter_block(self, workflow: str, filter_name: str) -> str:
        pattern = rf"(?ms)^            {re.escape(filter_name)}:\n(?P<body>.*?)(?=^            [A-Za-z0-9_-]+:|^\s{2,}\S|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare paths-filter entry {filter_name}")
        if match is None:
            self.fail(f"workflow should declare paths-filter entry {filter_name}")
        return match.group("body")

    def _step_block(self, workflow: str, step_name: str) -> str:
        pattern = rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare step {step_name}")
        if match is None:
            self.fail(f"workflow should declare step {step_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
