import pathlib
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
        self.assertIn("scripts/tests/**", workflow)
        self.assertIn("actions/setup-node", workflow)
        self.assertIn("node-version: '22'", workflow)
        self.assertIn("npm ci", workflow)
        self.assertIn("npm run lint", workflow)
        self.assertIn("npm test", workflow)
        self.assertIn("node --test scripts/tests/test_promote_google_play_track.js", workflow)
        self.assertIn("python3 -m py_compile scripts/notify-discord-deploy.py", workflow)

    def test_operator_docs_name_ops_ci_responsibility(self):
        docs = [GIT_WORKFLOW_DOC.read_text(), RELEASE_CONTEXT_DOC.read_text()]
        for doc in docs:
            self.assertIn("Ops CI", doc)
            self.assertIn("functions/", doc)
            self.assertIn("scripts/promote-google-play-track.js", doc)
            self.assertIn("scripts/notify-discord-deploy.py", doc)


if __name__ == "__main__":
    unittest.main()
