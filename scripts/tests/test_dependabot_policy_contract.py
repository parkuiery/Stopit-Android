import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEPENDABOT_CONFIG = REPO_ROOT / ".github" / "dependabot.yml"
DEPENDENCY_RUNBOOK = REPO_ROOT / "docs" / "DEPENDENCY_LINT_MAINTENANCE.md"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"


class DependabotPolicyContractTest(unittest.TestCase):
    def test_dependabot_policy_covers_stopit_dependency_ecosystems(self):
        self.assertTrue(
            DEPENDABOT_CONFIG.exists(),
            "#693 requires a Dependabot policy file for dependency update automation",
        )
        config = DEPENDABOT_CONFIG.read_text()

        self.assertIn("version: 2", config)
        expected_ecosystems = {
            "gradle": "/",
            "github-actions": "/",
            "npm": "/functions",
            "bun": "/tools/aso-screenshots",
        }
        for ecosystem, directory in expected_ecosystems.items():
            with self.subTest(ecosystem=ecosystem, directory=directory):
                pattern = (
                    rf"package-ecosystem:\s*[\"']?{re.escape(ecosystem)}[\"']?[\s\S]*?"
                    rf"directory:\s*[\"']?{re.escape(directory)}[\"']?"
                )
                self.assertRegex(config, pattern)

    def test_dependabot_policy_limits_noise_and_marks_manual_major_review(self):
        config = DEPENDABOT_CONFIG.read_text()

        self.assertIn("schedule:", config)
        self.assertGreaterEqual(config.count("interval: weekly"), 4)
        self.assertIn("open-pull-requests-limit:", config)
        self.assertIn('"maintenance"', config)
        self.assertIn('"automation"', config)
        self.assertIn('"dependencies"', config)
        self.assertIn("groups:", config)
        self.assertIn("patterns:", config)
        self.assertIn("ignore:", config)
        self.assertIn("update-types:", config)
        self.assertIn("version-update:semver-major", config)

    def test_operator_docs_explain_dependabot_triage_and_release_boundary(self):
        docs = DEPENDENCY_RUNBOOK.read_text() + "\n" + GIT_WORKFLOW_DOC.read_text()

        for required in [
            "Dependabot",
            "#693",
            "weekly",
            "maintenance",
            "automation",
            "major update",
            "수동 검토",
            "Play deploy",
            "release secret",
            ".github/dependabot.yml",
            "dependabot/*",
            "Branch Hygiene",
        ]:
            with self.subTest(required=required):
                self.assertIn(required, docs)


if __name__ == "__main__":
    unittest.main()
