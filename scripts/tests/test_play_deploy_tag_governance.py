import pathlib
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "play-deploy.yml"
PLAY_DOC_PATH = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
GIT_WORKFLOW_DOC_PATH = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
RELEASE_CONTEXT_PATH = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class PlayDeployTagGovernanceTest(unittest.TestCase):
    def test_workflow_dispatch_requires_semver_tag_ref_for_all_tracks(self):
        workflow = WORKFLOW_PATH.read_text()

        self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("Manual Play deploys require a SemVer tag ref", workflow)
        self.assertIn("internal, alpha, beta, and production", workflow)

    def test_play_deployment_doc_mentions_tag_governance_for_manual_dispatch(self):
        doc = PLAY_DOC_PATH.read_text()

        self.assertIn("Manual CD `workflow_dispatch` still requires a SemVer tag ref", doc)
        self.assertIn("branch refs are rejected for `internal`, `alpha`, `beta`, and `production`", doc)

    def test_git_workflow_doc_mentions_manual_play_dispatch_tag_governance(self):
        doc = GIT_WORKFLOW_DOC_PATH.read_text()

        self.assertIn("manual `workflow_dispatch`", doc)
        self.assertIn("SemVer tag ref", doc)
        self.assertIn("branch ref", doc)

    def test_release_context_mentions_manual_non_production_tag_guard(self):
        doc = RELEASE_CONTEXT_PATH.read_text()

        self.assertIn("manual dispatch도 SemVer tag ref에서만 허용", doc)
        self.assertIn("internal/alpha/beta/production", doc)


if __name__ == "__main__":
    unittest.main()
