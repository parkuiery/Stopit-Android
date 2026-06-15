import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
WRAPPER_PROPERTIES = REPO_ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
GIT_WORKFLOW = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
PLAY_DEPLOYMENT = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CHECKLIST = REPO_ROOT / "docs" / "RELEASE_CHECKLIST.md"
RELEASE_CONTEXT = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"
AUTOMATION_OPS = REPO_ROOT / "docs" / "ops" / "stopit" / "automation-ops.md"
OPS_CI = REPO_ROOT / ".github" / "workflows" / "ops-ci.yml"


class GradleWrapperDistributionChecksumTest(unittest.TestCase):
    def test_gradle_distribution_url_has_sha256_checksum(self):
        properties = self._read_properties()

        distribution_url = properties.get("distributionUrl", "")
        checksum = properties.get("distributionSha256Sum", "")

        self.assertIn("services.gradle.org/distributions/gradle-", distribution_url)
        self.assertTrue(
            distribution_url.endswith("-bin.zip"),
            "Stopit release/build workflows use the bin Gradle distribution; checksum must match that artifact.",
        )
        self.assertRegex(
            checksum,
            r"^[0-9a-f]{64}$",
            "gradle-wrapper.properties must pin distributionSha256Sum for the Gradle distribution zip.",
        )

    def test_operator_docs_describe_wrapper_distribution_checksum_contract(self):
        docs = {
            "docs/GIT_WORKFLOW.md": GIT_WORKFLOW.read_text(),
            "docs/PLAY_DEPLOYMENT.md": PLAY_DEPLOYMENT.read_text(),
            "docs/RELEASE_CHECKLIST.md": RELEASE_CHECKLIST.read_text(),
            "docs/ops/stopit/release-context.md": RELEASE_CONTEXT.read_text(),
        }

        for doc_name, doc in docs.items():
            with self.subTest(doc=doc_name):
                self.assertIn("distributionSha256Sum", doc)
                self.assertIn("gradle-wrapper.properties", doc)
                self.assertIn("gradle-8.11.1-bin.zip.sha256", doc)
                self.assertIn("wrapper-validation", doc)

    def test_ops_ci_docs_contract_runs_checksum_guard(self):
        workflow = OPS_CI.read_text()
        automation_ops = AUTOMATION_OPS.read_text()

        self.assertIn("scripts/tests/test_gradle_wrapper_distribution_checksum.py", workflow)
        self.assertIn("scripts.tests.test_gradle_wrapper_distribution_checksum", workflow)
        self.assertIn("test_gradle_wrapper_distribution_checksum", automation_ops)

    def _read_properties(self) -> dict[str, str]:
        properties: dict[str, str] = {}
        for raw_line in WRAPPER_PROPERTIES.read_text().splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            properties[key] = value
        return properties


if __name__ == "__main__":
    unittest.main()
