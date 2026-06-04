import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID_CI_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "android-ci.yml"
RELEASE_BUILD_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-build.yml"
PLAY_DEPLOY_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class AndroidCiArtifactRetentionTest(unittest.TestCase):
    def test_prod_debug_apk_artifact_has_short_retention(self):
        workflow = ANDROID_CI_WORKFLOW.read_text()
        upload_step = self._step_block(workflow, "Upload prod debug APK")

        self.assertIn("uses: actions/upload-artifact@v7", upload_step)
        self.assertIn("name: stopit-prod-debug-apk", upload_step)
        self.assertIn("retention-days: 7", upload_step)

    def test_signed_release_artifacts_keep_longer_retention(self):
        release_build = RELEASE_BUILD_WORKFLOW.read_text()
        play_deploy = PLAY_DEPLOY_WORKFLOW.read_text()

        self.assertIn("retention-days: 30", release_build)
        self.assertIn("retention-days: 30", play_deploy)

    def test_operator_docs_explain_artifact_quota_boundary(self):
        docs = [
            PLAY_DEPLOYMENT_DOC.read_text(),
            GIT_WORKFLOW_DOC.read_text(),
            RELEASE_CONTEXT_DOC.read_text(),
        ]

        for doc in docs:
            with self.subTest(doc=doc[:40]):
                self.assertIn("stopit-prod-debug-apk", doc)
                self.assertIn("retention-days: 7", doc)
                self.assertIn("Artifact storage quota has been hit", doc)
                self.assertIn("6–12", doc)

    def _step_block(self, workflow: str, step_name: str) -> str:
        pattern = rf"(?ms)^      - name: {re.escape(step_name)}\n(?P<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:|\Z)"
        match = re.search(pattern, workflow)
        self.assertIsNotNone(match, f"workflow should declare step {step_name}")
        if match is None:
            self.fail(f"workflow should declare step {step_name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
