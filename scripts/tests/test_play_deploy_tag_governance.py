import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
GIT_WORKFLOW_DOC_PATH = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CHECKLIST_PATH = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT_PATH = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
FUNCTIONS_README_PATH = REPO_ROOT / "functions" / "README.md"


class PlayDeployTagGovernanceTest(unittest.TestCase):
    def test_workflow_dispatch_requires_semver_tag_ref_for_all_tracks(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("Manual Play deploys require a SemVer tag ref", workflow)
        self.assertIn("internal, alpha, beta, and production", workflow)

    def test_production_completion_marker_requires_completed_release_status(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("- name: Mark production deployment complete", workflow)
        self.assertIn("if: success() && env.DEPLOY_TRACK == 'production' && env.RELEASE_STATUS == 'completed'", workflow)

    def test_play_deployment_doc_mentions_tag_governance_for_manual_dispatch(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("Manual CD `workflow_dispatch` still requires a SemVer tag ref", doc)
        self.assertIn("branch refs are rejected for `internal`, `alpha`, `beta`, and `production`", doc)

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
            "# branch ref로 internal/alpha/beta/production 업로드 우회 금지\n\n"
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
        self.assertIn("Discord", docs)


if __name__ == "__main__":
    unittest.main()
