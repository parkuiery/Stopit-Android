import pathlib
import re
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
RELEASE_QA_WORKFLOW = REPO_ROOT / ".github" / "workflows" / "release-qa.yml"
GIT_WORKFLOW_DOC = REPO_ROOT / "docs" / "GIT_WORKFLOW.md"
PLAY_DEPLOYMENT_DOC = REPO_ROOT / "docs" / "PLAY_DEPLOYMENT.md"
RELEASE_CONTEXT_DOC = REPO_ROOT / "docs" / "ops" / "stopit" / "release-context.md"


class ReleaseQaWorkflowScopeTest(unittest.TestCase):
    def _job_body(self, workflow: str, job_name: str) -> str:
        match = re.search(
            rf"(?ms)^  {re.escape(job_name)}:\n(?P<body>.*?)(?=^  [A-Za-z0-9_-]+:|\Z)",
            workflow,
        )
        self.assertIsNotNone(match, f"{job_name} job should exist")
        if match is None:
            self.fail(f"{job_name} job should exist")
        return match.group("body")

    def test_manual_dispatch_ref_guard_runs_before_release_qa_secrets_and_emulator(self):
        workflow = RELEASE_QA_WORKFLOW.read_text()

        for job_name in ("full-release-qa", "release-instrumentation-qa"):
            with self.subTest(job=job_name):
                job_body = self._job_body(workflow, job_name)
                guard_index = job_body.find("Validate manual release QA ref")
                firebase_index = job_body.find("Restore Firebase google-services.json files")
                emulator_index = job_body.find("Enable KVM for Android emulator")

                self.assertNotEqual(
                    guard_index,
                    -1,
                    "manual Release QA runs must validate the selected ref before reading Firebase secrets or starting emulator work",
                )
                self.assertNotEqual(firebase_index, -1, "Release QA jobs should still restore Firebase when allowed to run")
                self.assertLess(
                    guard_index,
                    firebase_index,
                    "manual Release QA ref guard must run before Firebase secrets are read",
                )
                if emulator_index != -1:
                    self.assertLess(
                        guard_index,
                        emulator_index,
                        "manual Release QA ref guard must run before emulator/KVM setup",
                    )

                self.assertIn("github.event_name == 'workflow_dispatch'", job_body)
                self.assertIn("GITHUB_REF_TYPE", job_body)
                self.assertIn("GITHUB_REF_NAME", job_body)
                self.assertIn('"main"', job_body)
                self.assertIn("release/*", job_body)
                self.assertIn("hotfix/*", job_body)
                self.assertIn("v[0-9]+\\.[0-9]+\\.[0-9]+", job_body)
                self.assertIn("will not restore Firebase secrets or run release QA", job_body)

    def test_operator_docs_describe_release_qa_manual_dispatch_scope(self):
        expected_scope = "manual dispatch from main/release/*/hotfix/*/semver tag refs"
        for path in (GIT_WORKFLOW_DOC, PLAY_DEPLOYMENT_DOC, RELEASE_CONTEXT_DOC):
            with self.subTest(path=path):
                text = path.read_text()
                normalized = text.replace("`", "").lower()
                self.assertIn("android release qa", normalized)
                self.assertIn(expected_scope, normalized)
                self.assertIn("will not restore firebase secrets or run release qa", normalized)


if __name__ == "__main__":
    unittest.main()
