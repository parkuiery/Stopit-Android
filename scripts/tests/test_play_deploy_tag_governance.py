import os
import pathlib
import subprocess
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
PLAY_SECRETS_RUNBOOK_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOY_SECRETS_RUNBOOK.md"
GIT_WORKFLOW_DOC_PATH = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CHECKLIST_PATH = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT_PATH = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
FUNCTIONS_README_PATH = REPO_ROOT / "functions" / "README.md"
ROLLOUT_VALIDATOR_PATH = REPO_ROOT / "scripts" / "validate-play-rollout-inputs.js"
PRODUCTION_ENVIRONMENT_GUARD_PATH = REPO_ROOT / "scripts" / "check-production-environment-approval.sh"


class PlayDeployTagGovernanceTest(unittest.TestCase):
    def test_workflow_dispatch_requires_full_release_guard_for_all_tracks(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("Manual Play deploys require a SemVer tag ref", workflow)
        self.assertIn("internal, alpha, beta, and production", workflow)
        self.assertIn("- name: Validate Play deploy release guardrails", workflow)
        guard_step = workflow.split("- name: Validate Play deploy release guardrails", 1)[1].split("- name:", 1)[0]
        self.assertIn("GH_TOKEN: ${{ github.token }}", guard_step)
        self.assertIn("run: scripts/validate-play-deploy-ref.sh", guard_step)

    def test_production_completion_marker_requires_completed_release_status(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("- name: Mark production deployment complete", workflow)
        self.assertIn("if: success() && env.DEPLOY_TRACK == 'production' && env.RELEASE_STATUS == 'completed'", workflow)

    def test_play_deployment_doc_mentions_tag_governance_for_manual_dispatch(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("Manual CD `workflow_dispatch` still requires the same SemVer tag ref release guard", doc)
        self.assertIn("branch refs are rejected for `internal`, `alpha`, `beta`, and `production`", doc)
        self.assertIn("origin/main reachable", doc)
        self.assertIn("previous SemVer production completion marker", doc)

    def test_play_deployment_doc_mentions_node22_helper_runtime(self):
        docs = "\n--- docs/PLAY_DEPLOYMENT.md ---\n" + PLAY_DOC_PATH.read_text()
        docs += "\n--- docs/ops/stopit/release-context.md ---\n" + RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("Node 22", docs)
        self.assertIn("actions/setup-node@v5", docs)
        self.assertIn("validate-play-rollout-inputs.js", docs)
        self.assertIn("promote-google-play-track.js", docs)

    def test_play_deployment_doc_defines_completion_marker_as_completed_production_only(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("only when `track=production` and `release_status=completed`", doc)
        self.assertIn("`draft`, `inProgress`, or `halted` production runs must not write", doc)

    def test_git_workflow_doc_defines_completion_marker_as_completed_production_only(self):
        doc = GIT_WORKFLOW_DOC_PATH.read_text()

        self.assertIn("`track=production` + `release_status=completed`", doc)
        self.assertIn("`draft`, `inProgress`, `halted` 상태는 production 완료 marker를 쓰지 않는다", doc)

    def test_git_workflow_doc_mentions_manual_play_dispatch_tag_governance(self):
        doc = GIT_WORKFLOW_DOC_PATH.read_text()

        self.assertIn("manual `workflow_dispatch`", doc)
        self.assertIn("SemVer tag ref", doc)
        self.assertIn("branch ref", doc)

    def test_git_workflow_release_flow_keeps_manual_dispatch_and_main_sync_in_same_code_block(self):
        doc = GIT_WORKFLOW_DOC_PATH.read_text()

        self.assertIn(
            "# 5-1. manual `workflow_dispatch`가 필요해도 같은 SemVer tag ref에서만 실행\n"
            "# branch ref로 internal/alpha/beta/production 업로드 우회 금지\n"
            "# 선택 tag도 origin/main reachable + 직전 production marker guard를 통과해야 함\n\n"
            "# 6. main -> develop 역머지\n"
            "git checkout develop\n"
            "git pull origin develop\n"
            "git merge origin/main\n"
            "git push origin develop",
            doc,
        )

    def test_release_context_mentions_manual_non_production_tag_guard(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("manual dispatch도 SemVer tag ref에서만 허용", doc)
        self.assertIn("internal/alpha/beta/production", doc)

    def test_release_context_defines_completion_marker_as_completed_production_only(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("`track=production` + `release_status=completed`", doc)
        self.assertIn("`draft`, `inProgress`, `halted` production dispatch", doc)

    def test_release_checklist_mentions_manual_dispatch_tag_governance(self):
        doc = RELEASE_CHECKLIST_PATH.read_text()

        self.assertIn("manual `workflow_dispatch`", doc)
        self.assertIn("SemVer tag ref", doc)
        self.assertIn("branch ref", doc)

    def test_play_deploy_production_track_uses_github_environment_gate(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("environment:", workflow)
        self.assertIn("inputs.track == 'production'", workflow)
        self.assertIn("production", workflow)
        self.assertIn("play-deploy-non-production", workflow)

    def test_play_deploy_pins_node22_before_js_helper_steps(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("- name: Set up Node 22", workflow)
        node_setup_step = workflow.split("- name: Set up Node 22", 1)[1].split("- name:", 1)[0]
        node_setup_index = workflow.index("- name: Set up Node 22")
        guardrail_index = workflow.index("- name: Validate Play deploy release guardrails")
        non_production_rollout_index = workflow.index("- name: Validate non-production staged rollout inputs")
        production_rollout_index = workflow.index("- name: Validate production staged rollout inputs")

        self.assertLess(node_setup_index, guardrail_index)
        self.assertLess(node_setup_index, non_production_rollout_index)
        self.assertLess(node_setup_index, production_rollout_index)
        self.assertIn("uses: actions/setup-node@v5", node_setup_step)
        self.assertIn("node-version: '22'", node_setup_step)

    def test_non_production_staged_rollout_inputs_are_validated_before_secret_decode(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("- name: Validate non-production staged rollout inputs", workflow)
        rollout_step = workflow.split("- name: Validate non-production staged rollout inputs", 1)[1].split("- name:", 1)[0]
        secret_step_index = workflow.index("- name: Validate build/upload deployment secrets")
        rollout_step_index = workflow.index("- name: Validate non-production staged rollout inputs")

        self.assertLess(rollout_step_index, secret_step_index)
        self.assertIn("if: env.DEPLOY_TRACK != 'production'", rollout_step)
        self.assertIn("run: node scripts/validate-play-rollout-inputs.js", rollout_step)

    def test_production_staged_rollout_inputs_are_validated_before_secret_decode(self):
        workflow = WORKFLOW_PATH.read_text()
        self.assertIn("- name: Validate production staged rollout inputs", workflow)
        rollout_step = workflow.split("- name: Validate production staged rollout inputs", 1)[1].split("- name:", 1)[0]
        secret_step_index = workflow.index("- name: Validate production promotion secrets")
        rollout_step_index = workflow.index("- name: Validate production staged rollout inputs")

        self.assertLess(rollout_step_index, secret_step_index)
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", rollout_step)
        self.assertIn("node - <<'NODE'", rollout_step)
        self.assertIn("validateReleaseStatusAndRolloutFraction", rollout_step)

    def test_production_environment_required_reviewer_guard_runs_before_production_secrets(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertTrue(PRODUCTION_ENVIRONMENT_GUARD_PATH.exists())
        script = PRODUCTION_ENVIRONMENT_GUARD_PATH.read_text()
        self.assertIn("repos/${repo}/environments/${environment_name}", script)
        self.assertIn("required_reviewers", script)
        self.assertIn("protection_rules", script)

        self.assertIn("- name: Verify production Environment approval protection", workflow)
        guard_step = workflow.split("- name: Verify production Environment approval protection", 1)[1].split("- name:", 1)[0]
        guard_step_index = workflow.index("- name: Verify production Environment approval protection")
        secret_step_index = workflow.index("- name: Validate production promotion secrets")

        self.assertLess(guard_step_index, secret_step_index)
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", guard_step)
        self.assertIn("GH_TOKEN: ${{ github.token }}", guard_step)
        self.assertIn("run: scripts/check-production-environment-approval.sh", guard_step)

    def run_rollout_validator(self, release_status, rollout_fraction):
        env = os.environ.copy()
        env["RELEASE_STATUS"] = release_status
        env["ROLLOUT_FRACTION"] = rollout_fraction
        return subprocess.run(
            ["node", str(ROLLOUT_VALIDATOR_PATH)],
            cwd=REPO_ROOT,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_non_production_staged_rollout_validator_accepts_valid_contract(self):
        valid_cases = [
            ("inProgress", "0.05", "Validated staged rollout input"),
            ("inProgress", "1", "Validated staged rollout input"),
            ("completed", "", "rollout_fraction is empty"),
            ("draft", "", "rollout_fraction is empty"),
            ("halted", "", "rollout_fraction is empty"),
        ]

        for release_status, rollout_fraction, expected_output in valid_cases:
            with self.subTest(release_status=release_status, rollout_fraction=rollout_fraction):
                result = self.run_rollout_validator(release_status, rollout_fraction)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(expected_output, result.stdout)

    def test_non_production_staged_rollout_validator_rejects_invalid_contract(self):
        invalid_cases = [
            ("inProgress", "", "RELEASE_STATUS=inProgress requires rollout_fraction"),
            ("inProgress", "0", "0 < rollout_fraction <= 1"),
            ("inProgress", "1.5", "0 < rollout_fraction <= 1"),
            ("inProgress", "not-a-number", "0 < rollout_fraction <= 1"),
            ("completed", "0.2", "Release statuses other than inProgress must leave rollout_fraction empty"),
            ("draft", "0.2", "Release statuses other than inProgress must leave rollout_fraction empty"),
            ("halted", "0.2", "Release statuses other than inProgress must leave rollout_fraction empty"),
        ]

        for release_status, rollout_fraction, expected_error in invalid_cases:
            with self.subTest(release_status=release_status, rollout_fraction=rollout_fraction):
                result = self.run_rollout_validator(release_status, rollout_fraction)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(expected_error, result.stderr)

    def test_non_production_staged_rollout_docs_define_secret_decode_boundary(self):
        docs = "\n--- docs/PLAY_DEPLOYMENT.md ---\n" + PLAY_DOC_PATH.read_text()
        docs += "\n--- docs/RELEASE_CHECKLIST.md ---\n" + RELEASE_CHECKLIST_PATH.read_text()
        docs += "\n--- docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md ---\n" + PLAY_SECRETS_RUNBOOK_PATH.read_text()
        docs += "\n--- docs/ops/stopit/release-context.md ---\n" + RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("non-production staged rollout", docs)
        self.assertIn("secret decode", docs)
        self.assertIn("`release_status=inProgress`", docs)
        self.assertIn("`0 < rollout_fraction <= 1`", docs)
        self.assertIn("`completed`/`draft`/`halted`", docs)

    def test_production_staged_rollout_docs_define_pre_secret_boundary(self):
        docs = "\n--- docs/PLAY_DEPLOYMENT.md ---\n" + PLAY_DOC_PATH.read_text()
        docs += "\n--- docs/PLAY_DEPLOY_SECRETS_RUNBOOK.md ---\n" + PLAY_SECRETS_RUNBOOK_PATH.read_text()
        docs += "\n--- docs/ops/stopit/release-context.md ---\n" + RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("production staged rollout", docs)
        self.assertIn("before `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`", docs)
        self.assertIn("`release_status=inProgress`", docs)
        self.assertIn("`0 < rollout_fraction <= 1`", docs)
        self.assertIn("`completed`/`draft`/`halted`", docs)

    def test_production_promotion_skips_android_build_setup_and_secret_bundle(self):
        workflow = WORKFLOW_PATH.read_text()

        for step_name in (
            "Set up JDK 17",
            "Set up Gradle",
            "Make Gradle wrapper executable",
            "Validate build/upload deployment secrets",
            "Decode signing, Firebase, and Play credentials",
            "Run release unit tests",
            "Build signed prod release bundle",
            "Upload signed AAB artifact",
            "Upload to Google Play",
        ):
            step = workflow.split(f"- name: {step_name}", 1)[1]
            step = step.split("- name:", 1)[0]
            self.assertIn("if: env.DEPLOY_TRACK != 'production'", step, step_name)

        production_secret_step = workflow.split("- name: Validate production promotion secrets", 1)[1]
        production_secret_step = production_secret_step.split("- name:", 1)[0]
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", production_secret_step)
        self.assertIn("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", production_secret_step)
        self.assertNotIn("ANDROID_KEYSTORE_BASE64", production_secret_step)
        self.assertNotIn("GOOGLE_SERVICES_JSON", production_secret_step)

        production_decode_step = workflow.split("- name: Decode Play credentials for production promotion", 1)[1]
        production_decode_step = production_decode_step.split("- name:", 1)[0]
        self.assertIn("if: env.DEPLOY_TRACK == 'production'", production_decode_step)
        self.assertIn("$GOOGLE_PLAY_SERVICE_ACCOUNT_PATH", production_decode_step)
        self.assertNotIn("$ANDROID_KEYSTORE_PATH", production_decode_step)
        self.assertNotIn("app/src/prod/google-services.json", production_decode_step)

    def test_docs_explain_production_promotion_minimal_secret_boundary(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("production promotion path does not decode the Android keystore", doc)
        self.assertIn("does not restore `GOOGLE_SERVICES_JSON`", doc)
        self.assertIn("does not run `:app:bundleProdRelease`", doc)
        self.assertIn("requires only `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` plus tag/versionCode governance", doc)

    def test_docs_explain_production_environment_required_reviewer_gate(self):
        docs = "\n--- docs/PLAY_DEPLOYMENT.md ---\n" + PLAY_DOC_PATH.read_text()
        docs += "\n--- docs/GIT_WORKFLOW.md ---\n" + GIT_WORKFLOW_DOC_PATH.read_text()
        docs += "\n--- docs/RELEASE_CHECKLIST.md ---\n" + RELEASE_CHECKLIST_PATH.read_text()
        docs += "\n--- docs/ops/stopit/release-context.md ---\n" + RELEASE_CONTEXT_PATH.read_text()
        docs += "\n--- functions/README.md ---\n" + FUNCTIONS_README_PATH.read_text()

        self.assertIn("GitHub Environment", docs)
        self.assertIn("production", docs)
        self.assertIn("required reviewer", docs)
        self.assertIn("scripts/check-production-environment-approval.sh", docs)
        self.assertIn("Discord", docs)


if __name__ == "__main__":
    unittest.main()
