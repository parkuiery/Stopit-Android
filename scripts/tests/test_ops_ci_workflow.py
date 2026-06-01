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

    def test_operator_docs_name_ops_ci_responsibility(self):
        docs = [GIT_WORKFLOW_DOC.read_text(), RELEASE_CONTEXT_DOC.read_text()]
        for doc in docs:
            self.assertIn("Ops CI", doc)
            self.assertIn("functions/", doc)
            self.assertIn("scripts/promote-google-play-track.js", doc)
            self.assertIn("scripts/notify-discord-deploy.py", doc)
            self.assertIn("release-helper guardrail", doc)
            self.assertIn("python3 -m unittest discover -s scripts/tests -p 'test_*.py'", doc)


if __name__ == "__main__":
    unittest.main()
