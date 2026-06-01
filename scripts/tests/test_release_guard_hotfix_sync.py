import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAY_DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
VERSION_GUARD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "version-guard.yml"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"


class ReleaseGuardHotfixSyncTest(unittest.TestCase):
    def test_play_deploy_tag_guard_step_passes_github_token_to_gh_cli(self):
        workflow = PLAY_DEPLOY_WORKFLOW.read_text()

        guard_step = workflow.split("- name: Validate tag-push release guardrails", 1)[1].split(
            "- name:", 1
        )[0]

        self.assertIn("GH_TOKEN: ${{ github.token }}", guard_step)
        self.assertIn("run: scripts/validate-play-deploy-ref.sh", guard_step)

    def test_version_guard_detects_android_version_file_changes_before_play_lookup(self):
        workflow = VERSION_GUARD_WORKFLOW.read_text()

        self.assertIn("- name: Detect Android version file changes", workflow)
        self.assertIn("id: version_changes", workflow)
        self.assertIn("app/build.gradle.kts", workflow)
        self.assertIn("steps.version_changes.outputs.changed == 'true'", workflow)

        detect_index = workflow.index("- name: Detect Android version file changes")
        restore_index = workflow.index("- name: Restore Google Play service account for guardrail")
        resolve_index = workflow.index("- name: Resolve base and Google Play versionCode maxima")
        validate_index = workflow.index("- name: Validate versionName and versionCode")
        self.assertLess(detect_index, restore_index)
        self.assertLess(detect_index, resolve_index)
        self.assertLess(detect_index, validate_index)

    def test_docs_explain_develop_release_guard_hotfix_sync_contract(self):
        docs = PLAY_DEPLOYMENT_DOC.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        self.assertIn("develop", docs)
        self.assertIn("GH_TOKEN", docs)
        self.assertIn("Version Guard", docs)
        self.assertIn("workflow-only", docs)
        self.assertIn("app/build.gradle.kts", docs)


if __name__ == "__main__":
    unittest.main()
